import httpClient from "@shared/api/instance";
import type { ListResult, Result } from "@shared/api/types";

/**
 * @deprecated 后端不存在 POST /user/scale/user 接口；
 * 提交测评请使用 POST /user/scale/assessment/submit（submitAssessment）。
 */

/** 我的测评记录查询参数 */
export interface ScaleRecordQuery {
  pageNum: number;
  pageSize: number;
  scaleId?: number;
  finishStatus?: number;
}

/** 我的测评记录行（与后端 ScaleRecordVO 对齐） */
export interface ScaleRecordVO {
  recordId: number;
  scaleId: number;
  scaleName: string;
  totalScore?: number;
  standardScore?: number;
  percentile?: number;
  /** 风险等级：0-无 1-低 2-中 3-高 */
  riskLevel?: number;
  resultText?: string;
  /** 完成状态：0-未完成 1-已完成 2-中途终止 */
  finishStatus?: number;
  startTime?: string;
  endTime?: string;
  createdTime?: string;
}

/** 分页获取我的测评记录（量表 / 完成状态筛选） */
export function getScaleRecords(data: ScaleRecordQuery) {
  return httpClient.get<ListResult<ScaleRecordVO>>("/user/scale/record/list", { params: data });
}

/** 获取作答记录详情 */
export function getScaleRecordDetail(recordId: string) {
  return httpClient.get<Result<any>>(`/user/scale/record/${recordId}`);
}

/** 获取单次测评的逐题答题明细 */
export function getScaleRecordAnswers(recordId: string) {
  return httpClient.get<Result<any[]>>(`/user/scale/record/${recordId}/answers`);
}

/* ==================== 测评（assessment）相关 ==================== */
/** 作答选项载荷（不含分数） */
export interface ScaleStartOption {
  optionId: number;
  optionText: string;
  sort: number;
}

/** 作答题目载荷（不含任何分数信息） */
export interface ScaleStartQuestion {
  questionId: number;
  title: string;
  /** 题目类型：1-单选 2-多选 3-填空 */
  questionType: number;
  /** 是否必答：0-否 1-是 */
  required: number;
  sort: number;
  dimensionId: number | null;
  options: ScaleStartOption[];
}

/** 精简跳题规则载荷：source 命中后隐藏 source 与 target 之间的题目（target 为空表示结束测评） */
export interface ScaleStartBranchRule {
  sourceQuestionId: number;
  sourceOptionId: number | null;
  targetQuestionId: number | null;
}

/** 开始测评返回（ScaleStartVO） */
export interface ScaleStartData {
  recordId: number;
  scaleId: number;
  scaleVersionId: number;
  scaleName: string;
  versionNo: string;
  description: string;
  copyrightInfo: string;
  /** 作答限时（秒），null 表示不限时 */
  timeLimit: number | null;
  /** 是否匿名测评：0-否 1-是 */
  anonymous: number;
  totalQuestionCount: number;
  questions: ScaleStartQuestion[];
  branchRules: ScaleStartBranchRule[];
}

/** 单题提交载荷（对齐后端 ScaleAnswerItemDTO） */
export interface ScaleAnswerItem {
  questionId: number;
  /** 作答状态：0-未作答 1-已作答 */
  answerStatus: number;
  /** 本题耗时（秒） */
  spendSeconds?: number;
  /** 单选传 1 个、多选传多个、填空传空数组 */
  optionIds: number[];
  /** 填空/简答答案 */
  answerText?: string;
}

/** 提交测评请求体 */
export interface ScaleSubmitPayload {
  recordId: number;
  answers: ScaleAnswerItem[];
}

/** 提交测评结果（ScaleSubmitResultVO） */
export interface ScaleSubmitResult {
  recordId: number;
  finishStatus: number;
  totalScore: number;
  standardScore: number;
  percentile: number;
  riskLevel: number;
  resultText: string;
  needFollowUp: boolean;
  dimensionResults: unknown[];
}

/** 查看未完成测评结果（ScaleUnfinishedVO） */
export interface ScaleUnfinished {
  hasUnfinished: boolean;
  recordId?: number;
  questionCount?: number;
  startTime?: string;
}

/** 开始测评：创建测评记录并下发整卷题目与跳题规则 */
export function startAssessment(data: { scaleId: number }) {
  return httpClient.post<Result<ScaleStartData>>("/user/scale/assessment/start", data);
}

/** 查询未完成测评：返回是否可续答及 recordId */
export function getUnfinishedAssessment(scaleId: number) {
  return httpClient.get<Result<ScaleUnfinished>>("/user/scale/assessment/unfinished", {
    params: { scaleId },
  });
}

/** 续答未完成测评：按 recordId 下发整卷题目（不新建记录） */
export function resumeAssessment(recordId: number) {
  return httpClient.post<Result<ScaleStartData>>("/user/scale/assessment/resume", { recordId });
}

/** 提交测评答案：服务端校验、计分、结果规则匹配与常模换算 */
export function submitAssessment(data: ScaleSubmitPayload) {
  return httpClient.post<Result<ScaleSubmitResult>>("/user/scale/assessment/submit", data);
}

/** 主动终止测评（放弃作答 / 超时退出） */
export function terminateAssessment(recordId: number) {
  return httpClient.post<Result<null>>(`/user/scale/assessment/${recordId}/terminate`);
}

/* ==================== AI对话量表工具（tool）相关 ==================== */

/** 保存用户测评记录ID：从对话卡片进入答题页后回写，保证刷新后仍可恢复作答上下文 */
export function bindToolRecord(data: { toolId: number; conversationId: number; recordId: number }) {
  return httpClient.post<Result<null>>("/user/scale/tool/bind-record", data);
}

/** 用户作答完成提交：answered=1 已作答（触发分析），answered=0 未作答（取消卡片） */
export function completeToolAnswer(data: { toolId: number; conversationId: number; answered: number; recordId?: number }) {
  return httpClient.post<Result<null>>("/user/scale/tool/complete", data);
}