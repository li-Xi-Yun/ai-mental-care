/**
 * AI 对话 STOMP 客户端（传输层，框架无关，不依赖 Vue）
 *
 * 设计要点：
 * - 每次 connect() 都重新获取一次性 wsTicket（10s 过期、使用即删），绝不缓存；
 * - 使用原生 WebSocket（/ws-native）以接收 TTS 二进制帧；
 * - STOMP CONNECT 头：{ token: <userToken> }；
 * - reconnectDelay = 0：不做自动重连（ticket 一次性，是否重连由业务层决定）；
 * - subscribe() 幂等：同一客户端同一时刻只保留一组会话订阅（切换会话时先清旧订阅）。
 */
import { Client, type IMessage, type StompSubscription } from "@stomp/stompjs";
import {
  asrIntermediateTopic,
  audioBinaryTopic,
  buildNativeWsUrl,
  conversationNameTopic,
  pendingActionTopic,
  textReplyTopic,
  STOMP_AUDIO_FRAME_DEST,
  WS_HEARTBEAT_INCOMING,
  WS_HEARTBEAT_OUTGOING,
} from "./endpoints";

/** 连接状态：idle(未连接/已主动关闭) → connecting → connected → closed/error */
export type StreamState = "idle" | "connecting" | "connected" | "closed" | "error";

/** 会话级流处理器（文本页/语音页按需传；未传的订阅不会建立） */
export interface ConversationStreamHandlers {
  /** AI 回复增量 chunk（逐字），需业务层做空闲 debounce 判定结束 */
  onTextReply?: (chunk: string) => void;
  /** 会话名推送 */
  onConversationName?: (name: string) => void;
  /** 待处理交互推送（原始 JSON 字符串，由业务层解析并守卫） */
  onPendingAction?: (rawJson: string) => void;
  /** TTS 二进制音频帧（裸 PCM16LE 单声道 24kHz） */
  onAudioBinary?: (pcm: Uint8Array) => void;
  /** ASR 中间识别结果 */
  onAsrIntermediate?: (text: string) => void;
}

export interface ConversationStompClientOptions {
  /** 获取一次性 ticket（调用方实现为直连后端的 HTTP 请求） */
  getTicket: () => Promise<string>;
  /** 后端直连 origin，如 http://localhost:8088 */
  getOrigin: () => string;
  /** 当前用户 token（作为 STOMP CONNECT 的 token 头） */
  getToken: () => string;
  onStateChange?: (state: StreamState, detail?: string) => void;
  /** 传输层被动关闭（非主动 deactivate；用于业务降级/重连提示） */
  onTransportClose?: (info: { code: number; reason: string }) => void;
  onStompError?: (detail: string) => void;
  debug?: (msg: string) => void;
}

/** 从 STOMP 消息中取出二进制载荷（优先 binaryBody，兼容 base64 文本帧兜底） */
function extractBinary(message: IMessage): Uint8Array {
  if (message.binaryBody && message.binaryBody.byteLength > 0) return message.binaryBody;
  const body = message.body;
  if (!body) return new Uint8Array(0);
  try {
    const bin = atob(body);
    const bytes = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
    return bytes;
  } catch {
    return new Uint8Array(0);
  }
}

export class ConversationStompClient {
  private readonly options: ConversationStompClientOptions;
  private client: Client | null = null;
  private subscriptions: StompSubscription[] = [];
  private _state: StreamState = "idle";
  private connectPromise: Promise<void> | null = null;

  constructor(options: ConversationStompClientOptions) {
    this.options = options;
  }

  get state(): StreamState {
    return this._state;
  }

  /** 建立连接（幂等：已连接直接返回；并发调用复用同一 Promise） */
  connect(): Promise<void> {
    if (this._state === "connected") return Promise.resolve();
    if (this.connectPromise) return this.connectPromise;
    this.connectPromise = this.doConnect().finally(() => {
      this.connectPromise = null;
    });
    return this.connectPromise;
  }

  private async doConnect(): Promise<void> {
    // 防御：旧连接仍存活时（如上次超时但握手其实成功）先彻底关闭，避免悬挂连接
    if (this.client?.active) {
      try {
        await this.client.deactivate();
      } catch {
        /* ignore */
      }
    }
    this.setState("connecting");

    const ticket = await this.options.getTicket();
    const url = buildNativeWsUrl(this.options.getOrigin(), ticket);

    await new Promise<void>((resolve, reject) => {
      let settled = false;
      const fail = (err: Error) => {
        if (!settled) {
          settled = true;
          reject(err);
        }
      };

      const client = new Client({
        webSocketFactory: () => new WebSocket(url),
        connectHeaders: { token: this.options.getToken() },
        reconnectDelay: 0,
        heartbeatIncoming: WS_HEARTBEAT_INCOMING,
        heartbeatOutgoing: WS_HEARTBEAT_OUTGOING,
        debug: this.options.debug ?? (() => {}),
        onConnect: () => {
          settled = true;
          this.setState("connected");
          resolve();
        },
        onStompError: (frame) => {
          const detail = frame.headers["message"] ?? "STOMP 协议错误";
          this.setState("error", detail);
          this.options.onStompError?.(detail);
          fail(new Error(detail));
        },
        onWebSocketClose: (evt) => {
          const wasActive = this._state === "connecting" || this._state === "connected";
          if (this._state !== "idle") this.setState("closed", evt.reason || "");
          if (wasActive) this.options.onTransportClose?.({ code: evt.code ?? 0, reason: evt.reason ?? "" });
          fail(new Error("WebSocket 连接关闭"));
        },
      });

      this.client = client;
      client.activate();
    });
  }

  /**
   * 绑定会话订阅（幂等：先清除旧订阅）。
   * 必须在已连接状态下调用；调用方需保证 subscribe 先于发送消息（避免丢首段回复）。
   */
  subscribe(conversationId: string, handlers: ConversationStreamHandlers): void {
    this.clearSubscriptions();
    const client = this.client;
    if (!client || this._state !== "connected") {
      console.warn("[STOMP] 未连接，跳过订阅 conversationId=", conversationId);
      return;
    }
    const add = (destination: string, onMessage: (message: IMessage) => void) => {
      this.subscriptions.push(client.subscribe(destination, onMessage));
    };

    if (handlers.onTextReply) {
      add(textReplyTopic(conversationId), (m) => handlers.onTextReply?.(m.body));
    }
    if (handlers.onConversationName) {
      add(conversationNameTopic(conversationId), (m) => handlers.onConversationName?.(m.body));
    }
    if (handlers.onPendingAction) {
      add(pendingActionTopic(conversationId), (m) => handlers.onPendingAction?.(m.body));
    }
    if (handlers.onAudioBinary) {
      add(audioBinaryTopic(conversationId), (m) => {
        const bytes = extractBinary(m);
        if (bytes.byteLength >= 2) handlers.onAudioBinary?.(bytes);
      });
    }
    if (handlers.onAsrIntermediate) {
      add(asrIntermediateTopic(conversationId), (m) => handlers.onAsrIntermediate?.(m.body));
    }
  }

  /** 清除全部订阅（不关闭连接） */
  clearSubscriptions(): void {
    for (const sub of this.subscriptions) {
      try {
        sub.unsubscribe();
      } catch {
        /* ignore */
      }
    }
    this.subscriptions = [];
  }

  /**
   * 发送音频帧。
   * @param audioBase64 base64 编码的裸 PCM16LE 单声道 16kHz（单帧 ≤4096 采样，约 11KB，低于 STOMP 16KB 帧上限）
   */
  publishAudioFrame(audioBase64: string, conversationId: string): boolean {
    const client = this.client;
    if (!client || this._state !== "connected") return false;
    client.publish({
      destination: STOMP_AUDIO_FRAME_DEST,
      body: JSON.stringify({ audioMessage: audioBase64, conversationId }),
    });
    return true;
  }

  /** 清订阅并关闭连接（可重复调用） */
  async deactivate(): Promise<void> {
    this.clearSubscriptions();
    // 先置 idle：主动关闭不应触发 onTransportClose 的业务降级逻辑
    this.setState("idle");
    const client = this.client;
    this.client = null;
    if (client?.active) {
      try {
        await client.deactivate();
      } catch {
        /* ignore */
      }
    }
  }

  private setState(state: StreamState, detail?: string): void {
    this._state = state;
    this.options.onStateChange?.(state, detail);
  }
}
