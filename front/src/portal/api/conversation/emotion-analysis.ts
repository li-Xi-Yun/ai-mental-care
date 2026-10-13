import httpClient from "@shared/api/instance";
import type { PageResult, Result } from "@shared/api/types";

/** 单轮情绪分析 VO（与后端 EmotionAnalysisVO 对齐） */
export interface EmotionAnalysisVO {
  /** 分析ID */
  id: number;
  /** 轮次 */
  roundNum: number;
  /** 情感分析详情 */
  analysisContent?: string;
  /** 情感标签（如 anger/neutral/happy 等） */
  emotionLabel?: string;
  /** 情感细分标签 */
  emotionSubLabel?: string;
  /** 情感识别置信度（0-1） */
  emotionConfidence?: number;
  /** 情绪强度（0-1） */
  emotionIntensity?: number;
  /** 较上一轮的情绪变化趋势 */
  emotionTrend?: string;
  /** PAD 愉悦度 P [‑1,1] */
  pScore?: number;
  /** PAD 唤醒度 A [‑1,1] */
  aScore?: number;
  /** PAD 支配度 D [‑1,1] */
  dScore?: number;
  /** 负向情绪占比（0-1） */
  negativeEmotionRatio?: number;
  /** 中性情绪占比（0-1） */
  neutralEmotionRatio?: number;
  /** 正向情绪占比（0-1） */
  positiveEmotionRatio?: number;
  /** 创建时间（时间戳或时间字符串，经拦截器转换） */
  createdTime?: number | string;
}

/** 情绪分析列表分页查询参数 */
export interface EmotionAnalysisQuery {
  pageNum: number;
  pageSize: number;
}

/** 情绪分析详情（含某一轮的对话内容） */
export interface EmotionAnalysisDetailVO {
  /** 模型（AI 回复）内容 */
  modelContent?: string;
  /** 用户消息内容 */
  userContent?: string;
  /** 思考内容 */
  thinkContent?: string;
}

/** 获取情绪分析列表（分页） */
export function getEmotionAnalysisList(conversationId: string, data: EmotionAnalysisQuery) {
  return httpClient.post<Result<PageResult<EmotionAnalysisVO>>>(
    `/user/conversation/emotion-analysis/list/${conversationId}`,
    data,
  );
}

/** 获取情绪分析详情 */
export function getEmotionAnalysisDetail(analysisId: string) {
  return httpClient.get<Result<EmotionAnalysisDetailVO>>(
    `/user/conversation/emotion-analysis/detail/${analysisId}`,
  );
}