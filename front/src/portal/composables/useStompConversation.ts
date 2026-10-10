/**
 * 会话 STOMP 连接（页面级实例）
 *
 * 每个页面（文本对话页 / 语音对话页）在 setup 中调用一次，各自持有独立连接；
 * 禁止做成模块级单例，避免页面卸载后残留连接。
 */
import { ref, type Ref } from "vue";
import { getToken } from "@shared/api/auth";
import { resolveBackendOrigin } from "@shared/ws/endpoints";
import { ConversationStompClient, type ConversationStreamHandlers, type StreamState } from "@shared/ws/stomp-client";
import { fetchWsTicket } from "@/portal/api/conversation/ws-ticket";

/** 默认连接超时（超过则判定失败，业务层走降级） */
const DEFAULT_CONNECT_TIMEOUT = 8000;

export interface UseStompConversationOptions {
  /** 传输层被动断开（非主动 dispose；流式中的页面据此降级/提示） */
  onTransportClose?: (info: { code: number; reason: string }) => void;
  /** STOMP 协议错误（如 token 失效） */
  onStompError?: (detail: string) => void;
}

export interface UseStompConversation {
  /** 连接状态（响应式，页面可用于提示/徽标） */
  state: Ref<StreamState>;
  /** 建立连接；超时或失败返回 false（不抛错，由业务层决定降级） */
  connect: (timeoutMs?: number) => Promise<boolean>;
  /** 绑定会话订阅（幂等，内部先清旧订阅） */
  bind: (conversationId: string, handlers: ConversationStreamHandlers) => void;
  /** 清除全部订阅 */
  unbindAll: () => void;
  /** 发送音频帧（base64 裸 PCM16LE 16k） */
  publishAudioFrame: (audioBase64: string, conversationId: string) => boolean;
  /** 清订阅并断开连接（页面卸载时调用，可重复调用） */
  dispose: () => Promise<void>;
}

export function useStompConversation(options: UseStompConversationOptions = {}): UseStompConversation {
  const state = ref<StreamState>("idle");

  const client = new ConversationStompClient({
    getTicket: fetchWsTicket,
    getOrigin: resolveBackendOrigin,
    getToken: () => getToken("user"),
    onStateChange: (next) => {
      state.value = next;
    },
    onTransportClose: options.onTransportClose,
    onStompError: options.onStompError,
    debug: import.meta.env.DEV ? (msg) => console.debug("[STOMP]", msg) : undefined,
  });

  async function connect(timeoutMs = DEFAULT_CONNECT_TIMEOUT): Promise<boolean> {
    let timer = 0;
    try {
      await Promise.race([
        client.connect(),
        new Promise<never>((_, reject) => {
          timer = window.setTimeout(() => reject(new Error("WebSocket 连接超时")), timeoutMs);
        }),
      ]);
      return client.state === "connected";
    } catch (e) {
      console.warn("[STOMP] 连接失败：", e);
      return false;
    } finally {
      if (timer) window.clearTimeout(timer);
    }
  }

  function bind(conversationId: string, handlers: ConversationStreamHandlers): void {
    client.subscribe(conversationId, handlers);
  }

  function unbindAll(): void {
    client.clearSubscriptions();
  }

  function publishAudioFrame(audioBase64: string, conversationId: string): boolean {
    return client.publishAudioFrame(audioBase64, conversationId);
  }

  async function dispose(): Promise<void> {
    await client.deactivate();
    state.value = client.state;
  }

  return { state, connect, bind, unbindAll, publishAudioFrame, dispose };
}
