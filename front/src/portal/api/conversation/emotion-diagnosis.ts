import httpClient from "@shared/api/instance";
import type { PageResult, Result } from "@shared/api/types";

/** 情绪诊断书响应 VO */
export interface EmotionDiagnosisVO {
  id?: number;
  conversationId?: number;
  userId?: number;
  roundNum?: number;
  diagnosisContent?: string;
  coreEmotionLabel?: string;
  coreEmotionConfAvg?: number;
  coreEmotionIntensityScore?: number;
  coreEmotion?: Record<string, number>;
  secondaryEmotion?: Record<string, number>;
  negativeEmotionRatio?: number;
  positiveEmotionRatio?: number;
  neutralEmotionRatio?: number;
  negativeEmotionDetail?: Record<string, number>;
  positiveEmotionDetail?: Record<string, number>;
  emotionTrend?: number;
  emotionPeakRound?: number;
  emotionValleyRound?: number;
  emotionFluctuationAmplitude?: number;
  emotionStableRounds?: number;
  emotionStabilityScore?: number;
  avgP?: number;
  avgA?: number;
  avgD?: number;
  stdP?: number;
  stdA?: number;
  stdD?: number;
  coreTriggerScene?: string;
  coreTriggerKeywords?: string;
  triggerRoundNum?: number;
  psychologicalState?: string;
  symptomSummary?: string;
  symptomTags?: string;
  socialFunctionImpact?: string;
  impactDomains?: string;
  dailyLifeInfluence?: string;
  symptomDuration?: string;
  onsetPattern?: string;
  firstTriggerDesc?: string;
  socialSupportLevel?: number;
  protectiveFactors?: string;
  copingStyle?: string;
  emotionRiskLevel?: number;
  emotionAdjustSuggestion?: string;
  needManualIntervene?: number;
  selfHarmRiskLevel?: number;
  suicideRiskLevel?: number;
  riskDetail?: string;
  crisisWarning?: number;
  selfHelpSuggestion?: string;
  socialSupportSuggestion?: string;
  professionalInterveneSuggestion?: string;
  suggestionPriority?: number;
  createdTime?: string;
  updatedTime?: string;
}

/** 诊断反馈展示 VO */
export interface AssessmentFeedbackVO {
  id?: number;
  diagnosisId?: number;
  userId?: number;
  diagnosisScore?: number | null;
  feedbackContent?: string | null;
  agreeRiskJudge?: number | null;
  agreeSuggestionSelf?: number | null;
  agreeSuggestionSocial?: number | null;
  agreeSuggestionProfessional?: number | null;
  useSuggestion?: number | null;
  feedbackTime?: string;
  createdTime?: string;
  updatedTime?: string;
}

/** 拥有诊断数据的会话列表展示 VO */
export interface DiagnosisConversationVO {
  conversationId?: number;
  conversationName?: string;
  latestRoundNum?: number;
  diagnosisCount?: number;
  diagnosisUpdatedTime?: string;
  conversationCreatedTime?: string;
}

/** 会话下诊断书列表展示 VO */
export interface EmotionDiagnosisListVO {
  id?: number;
  conversationId?: number;
  roundNum?: number;
  createdTime?: string;
  updatedTime?: string;
}

/** 分页查询拥有诊断数据的会话列表 */
export function getEmotionDiagnosisList(data: unknown) {
  return httpClient.post<Result<PageResult<DiagnosisConversationVO>>>("/user/conversation/emotion-diagnosis/list", data);
}

/** 分页查询某会话下的诊断书列表 */
export function getEmotionDiagnosisListByConversation(conversationId: string, data: unknown) {
  return httpClient.post<Result<PageResult<EmotionDiagnosisListVO>>>(
    `/user/conversation/emotion-diagnosis/list/${conversationId}`,
    data,
  );
}

/** 获取情绪诊断详情 */
export function getEmotionDiagnosisDetail(diagnosisId: string) {
  return httpClient.get<Result<EmotionDiagnosisVO>>(`/user/conversation/emotion-diagnosis/detail/${diagnosisId}`);
}

/** 获取诊断反馈详情 */
export function getDiagnosisFeedback(diagnosisId: string) {
  return httpClient.get<Result<AssessmentFeedbackVO>>(`/user/conversation/assessment-feedback/${diagnosisId}`);
}

/** 提交或更新情绪诊断反馈（后端 PUT /user/conversation/assessment-feedback/{id}） */
export function submitDiagnosisFeedback(diagnosisId: string, data: Partial<AssessmentFeedbackVO>) {
  return httpClient.put<Result<void>>(`/user/conversation/assessment-feedback/${diagnosisId}`, data);
}

/** 删除诊断反馈（可选，后端已提供） */
export function deleteDiagnosisFeedback(diagnosisId: string) {
  return httpClient.delete<Result<void>>(`/user/conversation/assessment-feedback/${diagnosisId}`);
}
