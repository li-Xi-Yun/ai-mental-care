import httpClient from "@shared/api/instance";
import type { Result } from "@shared/api/types";

/** 发送用户消息（后端实际路由为 /user/conversation/adapter/text/send） */
export function sendUserMessage(data: { message: string; conversationId?: number | string }) {
  return httpClient.post<Result<any>>("/user/conversation/adapter/text/send", data);
}