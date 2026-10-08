import httpClient from "@shared/api/instance";
import type { ListResult, Result } from "@shared/api/types";

/** 量表分页查询参数 */
export interface ScalePageQuery {
  /** 页码（从 1 开始） */
  pageNum: number;
  /** 每页条数 */
  pageSize: number;
  /** 量表类别 ID */
  scaleCategoryId?: number;
  /** 量表名称关键字 */
  keyword?: string;
}

/** 量表列表展示项（与后端 ScaleVO 对齐） */
export interface ScaleVO {
  id: number;
  scaleName: string;
  scaleCategoryId: number;
  /** 是否允许重复作答：0-否 1-是 */
  allowRepeat: number;
  /** 重复作答冷却时间（分钟） */
  coolMinutes: number;
  /** 作答限时（秒），null 表示不限时 */
  timeLimit: number | null;
  /** 是否匿名测评：0-否 1-是 */
  anonymous: number;
  versionNo: string;
  description: string;
  /** 当前版本题目总数 */
  questionCount: number;
  /** 预估用时（分钟） */
  estimatedMinutes: number;
}

/** 量表详情（在列表字段基础上增加维度与题型统计） */
export interface ScaleDetailVO extends ScaleVO {
  dimensions: string[];
  questionTypes: { questionType: number; questionTypeLabel: string; count: number }[];
}

/** 量表类别 */
export interface ScaleCategoryVO {
  id: number;
  categoryName: string;
  sort: number;
  /** 该类别下可作答量表数量 */
  scaleCount: number;
}

/** 分页获取量表列表 */
export function getScaleList(params: ScalePageQuery) {
  return httpClient.get<ListResult<ScaleVO>>("/user/scale/list", { params });
}

/** 获取量表详情 */
export function getScaleDetail(scaleId: number | string) {
  return httpClient.get<Result<ScaleDetailVO>>(`/user/scale/${scaleId}`);
}
