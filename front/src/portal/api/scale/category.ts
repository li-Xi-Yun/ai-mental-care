import httpClient from "@shared/api/instance";
import type { Result } from "@shared/api/types";

/** 获取量表分类列表 */
export function getScaleCategoryList() {
  return httpClient.get<Result<any[]>>("/user/scale/category");
}