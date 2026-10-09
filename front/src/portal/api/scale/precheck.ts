import httpClient from "@shared/api/instance";
import type { Result } from "@shared/api/types";

/** 作答前检查 VO（与后端 ScalePrecheckVO 对齐） */
export interface ScalePrecheckVO {
  /** 是否允许开始作答 */
  allowed: boolean;
  /** 不允许时的原因枚举：SCALE_DISABLED/NO_VERSION/COOLING/REPEAT_LIMITED/UNFINISHED_EXISTS */
  reason?: string;
  /** 若处于冷却期，剩余需等待分钟数（否则为 0） */
  coolRemainMinutes?: number;
}

/** 作答前检查：校验量表能否开始作答 */
export function getScalePrecheck(scaleId: number | string) {
  return httpClient.get<Result<ScalePrecheckVO>>(`/user/scale/${scaleId}/precheck`);
}