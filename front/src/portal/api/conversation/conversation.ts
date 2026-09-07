import httpClient from "@shared/api/instance";
import type { Result } from "@shared/api/types";

/** 获取对话列表 */
export function getConversationList(data: any) {
  return httpClient.post<Result<any[]>>("/user/conversation/list", data);
}

/** 修改对话名称 */
export function updateConversationName(conversationId: string, name: string) {
  return httpClient.put<Result<void>>(`/user/conversation/${conversationId}/name`, { name });
}

/** 删除对话 */
export function deleteConversation(conversationId: string) {
  return httpClient.delete<Result<void>>(`/user/conversation/${conversationId}`);
}