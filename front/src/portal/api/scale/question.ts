import httpClient from "@shared/api/instance";
import type { Result } from "@shared/api/types";

/** 获取量表题目列表 */
export function getScaleQuestionList(data: any) {
  return httpClient.get<Result<any[]>>("/user/scale/questions/list", { params: data });
}