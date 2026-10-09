import httpClient from "@shared/api/instance";
import type { Result } from "@shared/api/types";

/** 对话生命周期初始化请求 DTO（与后端 ConversationLifecycleInitDTO 对齐） */
export interface ConversationLifecycleInitDTO {
  /** 会话ID（可选），为空时自动创建新会话 */
  conversationId?: number;
  /** 输入数据类型集合，如 TEXT、AUDIO */
  inputTypes: string[];
  /** 输出数据类型集合，如 TEXT、AUDIO */
  outputTypes: string[];
}

/** 端点信息（与后端 ConversationLifecycleInitVO.EndpointInfo 对齐） */
export interface ConversationEndpointInfo {
  /** 端点描述，如 text-input */
  description: string;
  /** WebSocket 路径，如 /ws/text/2412321342421 */
  path: string;
}

/** 对话生命周期初始化响应 VO（与后端 ConversationLifecycleInitVO 对齐） */
export interface ConversationLifecycleInitVO {
  /** 会话ID */
  conversationId: number;
  /** 输入层端点信息列表 */
  inputEndpoints: ConversationEndpointInfo[];
  /** 输出层端点信息列表 */
  outputEndpoints: ConversationEndpointInfo[];
}

/** 初始化对话生命周期 */
export function initConversationLifecycle(data?: ConversationLifecycleInitDTO) {
  return httpClient.post<Result<ConversationLifecycleInitVO>>("/user/conversation/lifecycle/init", data);
}

/** 销毁对话生命周期 */
export function deleteConversationLifecycle(conversationId: number | string) {
  return httpClient.delete<Result<void>>(`/user/conversation/lifecycle/${conversationId}`);
}