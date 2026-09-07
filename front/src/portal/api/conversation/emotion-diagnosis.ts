import httpClient from "@shared/api/instance";
import type { Result } from "@shared/api/types";

/** 获取情绪诊断列表 */
export function getEmotionDiagnosisList(data: any) {
  return httpClient.post<Result<any[]>>("/user/conversation/emotion-diagnosis/list", data);
}

/** 获取某对话的情绪诊断列表 */
export function getEmotionDiagnosisListByConversation(conversationId: string, data: any) {
  return httpClient.post<Result<any[]>>(`/user/conversation/emotion-diagnosis/list/${conversationId}`, data);
}

/** 获取情绪诊断详情 */
export function getEmotionDiagnosisDetail(diagnosisId: string) {
  return httpClient.get<Result<any>>(`/user/conversation/emotion-diagnosis/detail/${diagnosisId}`);
}

/** 提交情绪诊断反馈 */
export function submitDiagnosisFeedback(diagnosisId: string, data: any) {
  return httpClient.put<Result<void>>(`/user/conversation/emotion-diagnosis/${diagnosisId}/feedback`, data);
}