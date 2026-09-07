import httpClient from "@shared/api/instance";
import type { Result } from "@shared/api/types";

/** 获取情绪分析列表 */
export function getEmotionAnalysisList(conversationId: string, data: any) {
  return httpClient.post<Result<any[]>>(`/user/conversation/emotion-analysis/list/${conversationId}`, data);
}

/** 获取情绪分析详情 */
export function getEmotionAnalysisDetail(analysisId: string) {
  return httpClient.get<Result<any>>(`/user/conversation/emotion-analysis/detail/${analysisId}`);
}