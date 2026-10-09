import httpClient from "@shared/api/instance";
import type { Result, ListResult } from "@shared/api/types";

/** 测评记录分页查询 DTO（extends PageBaseDTO：pageNum/pageSize） */
export interface AdminScaleRecordQueryDTO {
  pageNum: number;
  pageSize: number;
  /** 用户ID */
  userId?: number;
  /** 量表ID */
  scaleId?: number;
  /** 量表名称（支持模糊匹配） */
  scaleName?: string;
  /** 风险等级：0=无 1=低 2=中 3=高（预警） */
  riskLevel?: number;
  /** 作答状态：0=未完成 1=已完成 2=中途终止 */
  finishStatus?: number;
  /** 开始时间（查询范围起） */
  startTime?: string;
  /** 结束时间（查询范围止） */
  endTime?: string;
}

/** 测评记录列表 VO */
export interface AdminScaleRecordVO {
  /** 测评记录ID */
  recordId: number;
  /** 用户ID */
  userId?: number;
  /** 用户姓名 */
  userName?: string;
  /** 量表ID */
  scaleId?: number;
  /** 量表名称 */
  scaleName?: string;
  /** 量表版本ID */
  scaleVersionId?: number;
  /** 量表版本号 */
  versionNo?: string;
  /** 本次测评使用的常模组ID */
  normGroupId?: number;
  /** 最终计算原始总分 */
  totalScore?: number | null;
  /** 标准分（如T分） */
  standardScore?: number | null;
  /** 百分等级快照 */
  percentile?: number | null;
  /** 风险等级：0=无 1=低 2=中 3=高（预警） */
  riskLevel?: number | null;
  /** 本次测评结果描述 */
  resultText?: string;
  /** 作答状态：0=未完成 1=已完成 2=中途终止 */
  finishStatus?: number | null;
  /** 开始作答时间 */
  startTime?: string;
  /** 提交时间 */
  endTime?: string;
  /** 记录创建时间 */
  createdTime?: string;
}

/** 维度得分 VO */
export interface AdminScaleDimScoreVO {
  /** 维度ID */
  dimensionId?: number;
  /** 维度名称 */
  dimensionName?: string;
  /** 维度得分快照 */
  dimScore?: number | null;
  /** 维度解读快照 */
  dimResult?: string;
  /** 维度风险等级：0=无 1=低 2=中 3=高 */
  riskLevel?: number | null;
}

/** 测评记录详情 VO（在 AdminScaleRecordVO 基础上增加维度得分） */
export interface AdminScaleRecordDetailVO extends AdminScaleRecordVO {
  /** 各维度得分结果 */
  dimensionResults?: AdminScaleDimScoreVO[];
}

/** 用户选中选项 VO */
export interface AdminScaleSelectedOptionVO {
  /** 选中选项ID */
  optionId?: number;
  /** 选项文本快照 */
  optionText?: string;
}

/** 用户答题明细 VO */
export interface AdminScaleAnswerDetailVO {
  /** 题目ID */
  questionId?: number;
  /** 题干快照 */
  questionTitle?: string;
  /** 题目类型快照：1=单选 2=多选 3=填空 */
  questionType?: number | null;
  /** 答题状态：0=未作答 1=已作答 */
  answerStatus?: number | null;
  /** 填空答案文本（仅填空题返回） */
  answerText?: string;
  /** 选中选项快照列表 */
  selectedOptions?: AdminScaleSelectedOptionVO[];
  /** 答题记录创建时间 */
  createdTime?: string;
}

/** 风险预警统计查询 DTO */
export interface AdminScaleRiskStatisticsQueryDTO {
  /** 量表ID，为空统计全部量表 */
  scaleId?: number;
  /** 开始时间（统计范围起） */
  startTime?: string;
  /** 结束时间（统计范围止） */
  endTime?: string;
}

/** 风险预警统计 VO */
export interface AdminScaleRiskStatisticsVO {
  /** 量表ID */
  scaleId?: number;
  /** 量表名称 */
  scaleName?: string;
  /** 测评总人次 */
  totalCount?: number;
  /** 无风险人次 */
  noRiskCount?: number;
  /** 低风险人次 */
  lowRiskCount?: number;
  /** 中风险人次 */
  mediumRiskCount?: number;
  /** 高风险人次 */
  highRiskCount?: number;
  /** 高风险占比（派生，高风险人次/总人次*100，约两位小数） */
  highRiskRate?: number | null;
}

/** 分页查询用户测评记录（POST /admin/scale/record/page） */
export function getScaleRecordPage(data: AdminScaleRecordQueryDTO) {
  return httpClient.post<ListResult<AdminScaleRecordVO>>("/admin/scale/record/page", data);
}

/** 获取测评记录详情（GET /admin/scale/record/{recordId}） */
export function getScaleRecordDetail(recordId: number | string) {
  return httpClient.get<Result<AdminScaleRecordDetailVO>>(`/admin/scale/record/${recordId}`);
}

/** 获取测评记录答题明细（GET /admin/scale/record/{recordId}/answers） */
export function getScaleRecordAnswers(recordId: number | string) {
  return httpClient.get<Result<AdminScaleAnswerDetailVO[]>>(`/admin/scale/record/${recordId}/answers`);
}

/** 风险预警统计（POST /admin/scale/record/risk/statistics） */
export function getScaleRecordRiskStatistics(data: AdminScaleRiskStatisticsQueryDTO) {
  return httpClient.post<Result<AdminScaleRiskStatisticsVO>>("/admin/scale/record/risk/statistics", data);
}