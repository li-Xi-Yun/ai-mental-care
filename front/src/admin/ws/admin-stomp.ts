/**
 * 管理端轻量 STOMP 客户端（Composable）
 *
 * 与服务端约定：
 * - 握手前用 admin-token 换取一次性 wsTicket（见 @/admin/api/ws-ticket.ts）；
 * - 原生 WebSocket 端点 /ws-native，STOMP CONNECT 头 { token: <adminToken> }；
 * - 只订阅文本 JSON 事件（如 /user/queue/file/vector/status/{fileId}），无二进制帧；
 * - 页面级实例：每页 setup 调用一次，卸载时 dispose，禁止模块级单例。
 */
import { ref, type Ref } from "vue";
import { Client, type IMessage, type StompSubscription } from "@stomp/stompjs";
import { getToken } from "@shared/api/auth";
import { buildNativeWsUrl, fileVectorStatusTopic, resolveBackendOrigin, WS_HEARTBEAT_INCOMING, WS_HEARTBEAT_OUTGOING } from "@shared/ws/endpoints";
import { fetchAdminWsTicket } from "@/admin/api/ws-ticket";

/** 连接状态 */
export type AdminStreamState = "idle" | "connecting" | "connected" | "closed" | "error";

export interface UseAdminStompOptions {
  /** 传输层被动断开回调 */
  onTransportClose?: (info: { code: number; reason: string }) => void;
  onStompError?: (detail: string) => void;
  debug?: (msg: string) => void;
}

export interface UseAdminStomp {
  state: Ref<AdminStreamState>;
  connect: (timeoutMs?: number) => Promise<boolean>;
  /** 订阅指定文件的状态事件（幂等：先清旧订阅） */
  subscribeFileStatus: (fileId: string, onEvent: (event: FileVectorStatusEvent) => void) => void;
  /** 清除全部订阅（不关闭连接） */
  unsubscribeAll: () => void;
  dispose: () => Promise<void>;
}

/** 后端推送的文件向量解析状态事件 */
export interface FileVectorStatusEvent {
  fileId: number | string;
  status: "PARSING" | "READING" | "TRANSFORMING" | "VECTORIZING" | "WRITING" | "COMPLETED" | "FAILED" | "INTERRUPTED" | string;
  progress?: number | null;
  processedChunks?: number | null;
  totalChunks?: number | null;
  message?: string | null;
  sequence: number;
  timestamp: number;
}

const DEFAULT_CONNECT_TIMEOUT = 8000;

export function useAdminStomp(options: UseAdminStompOptions = {}): UseAdminStomp {
  const state = ref<AdminStreamState>("idle");
  let client: Client | null = null;
  let subscriptions: StompSubscription[] = [];

  async function connect(timeoutMs = DEFAULT_CONNECT_TIMEOUT): Promise<boolean> {
    let timer = 0;
    try {
      await Promise.race([
        doConnect(),
        new Promise<never>((_, reject) => {
          timer = window.setTimeout(() => reject(new Error("WebSocket 连接超时")), timeoutMs);
        }),
      ]);
      return state.value === "connected";
    } catch (e) {
      console.warn("[AdminSTOMP] 连接失败：", e);
      return false;
    } finally {
      if (timer) window.clearTimeout(timer);
    }
  }

  async function doConnect(): Promise<void> {
    // 防御：旧连接仍存活时先关闭
    if (client?.active) {
      try {
        await client.deactivate();
      } catch {
        /* ignore */
      }
    }
    state.value = "connecting";

    const ticket = await fetchAdminWsTicket();
    const url = buildNativeWsUrl(resolveBackendOrigin(), ticket);

    await new Promise<void>((resolve, reject) => {
      let settled = false;
      const fail = (err: Error) => {
        if (!settled) {
          settled = true;
          reject(err);
        }
      };

      const stomp = new Client({
        webSocketFactory: () => new WebSocket(url),
        connectHeaders: { token: getToken("admin") },
        reconnectDelay: 0,
        heartbeatIncoming: WS_HEARTBEAT_INCOMING,
        heartbeatOutgoing: WS_HEARTBEAT_OUTGOING,
        debug: options.debug ?? (() => {}),
        onConnect: () => {
          settled = true;
          state.value = "connected";
          resolve();
        },
        onStompError: (frame) => {
          const detail = frame.headers["message"] ?? "STOMP 协议错误";
          state.value = "error";
          options.onStompError?.(detail);
          fail(new Error(detail));
        },
        onWebSocketClose: (evt) => {
          const wasActive = state.value === "connecting" || state.value === "connected";
          if (state.value !== "idle") state.value = "closed";
          if (wasActive) options.onTransportClose?.({ code: evt.code ?? 0, reason: evt.reason ?? "" });
          fail(new Error("WebSocket 连接关闭"));
        },
      });

      client = stomp;
      stomp.activate();
    });
  }

  function subscribeFileStatus(fileId: string, onEvent: (event: FileVectorStatusEvent) => void): void {
    unsubscribeAll();
    const stomp = client;
    if (!stomp || state.value !== "connected") {
      console.warn("[AdminSTOMP] 未连接，跳过订阅 fileId=", fileId);
      return;
    }
    const destination = fileVectorStatusTopic(fileId);
    const handler = (msg: IMessage) => {
      try {
        const parsed = JSON.parse(msg.body ?? "{}") as FileVectorStatusEvent;
        onEvent(parsed);
      } catch (e) {
        console.warn("[AdminSTOMP] 状态事件解析失败：", e);
      }
    };
    subscriptions.push(stomp.subscribe(destination, handler));
  }

  function unsubscribeAll(): void {
    for (const sub of subscriptions) {
      try {
        sub.unsubscribe();
      } catch {
        /* ignore */
      }
    }
    subscriptions = [];
  }

  async function dispose(): Promise<void> {
    unsubscribeAll();
    state.value = "idle";
    const stomp = client;
    client = null;
    if (stomp?.active) {
      try {
        await stomp.deactivate();
      } catch {
        /* ignore */
      }
    }
  }

  return { state, connect, subscribeFileStatus, unsubscribeAll, dispose };
}
