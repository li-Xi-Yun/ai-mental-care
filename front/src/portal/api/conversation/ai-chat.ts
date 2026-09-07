import httpClient from "@shared/api/instance";
import type { Result } from "@shared/api/types";

/** 发送用户消息 */
export function sendUserMessage(data: any) {
  return httpClient.post<Result<any>>("/user/conversation/ai-chat/user-message", data);
}