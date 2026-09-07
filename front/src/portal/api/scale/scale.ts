import httpClient from "@shared/api/instance";
import type { Result } from "@shared/api/types";

/** 获取量表列表 */
export function getScaleList() {
  return httpClient.get<Result<any[]>>("/user/scale/list");
}