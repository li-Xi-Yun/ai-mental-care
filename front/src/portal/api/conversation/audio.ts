import httpClient from "@shared/api/instance";
import type { Result } from "@shared/api/types";

/** 初始化音频会话 */
export function initAudioSession(data: any) {
  return httpClient.post<Result<any>>("/user/conversation/audio/init", data);
}

/** 结束音频会话 */
export function endAudioSession(conversationId: string) {
  return httpClient.delete<Result<void>>(`/user/conversation/audio/${conversationId}/end`);
}