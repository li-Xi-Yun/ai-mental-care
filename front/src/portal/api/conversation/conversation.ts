import httpClient from "@shared/api/instance";
import type { Result } from "@shared/api/types";

/** 会话信息展示 VO */
export interface ConversationVO {
  id?: number;
  name?: string;
  createdTime?: string;
  updatedTime?: string;
  lastActiveTime?: string;
  currentRound?: number;
}

/** 获取对话列表 */
export function getConversationList(data: any) {
  return httpClient.post<Result<any[]>>("/user/conversation/list", data);
}

/** 查询会话详情 */
export function getConversationDetail(conversationId: string) {
  return httpClient.get<Result<ConversationVO>>(`/user/conversation/${conversationId}`);
}

/** 修改对话名称 */
export function updateConversationName(conversationId: string, name: string) {
  return httpClient.put<Result<void>>(`/user/conversation/${conversationId}/name`, { name });
}

/** 删除对话 */
export function deleteConversation(conversationId: string) {
  return httpClient.delete<Result<void>>(`/user/conversation/${conversationId}`);
}