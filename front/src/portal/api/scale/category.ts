import httpClient from "@shared/api/instance";
import type { Result } from "@shared/api/types";
import type { ScaleCategoryVO } from "./scale";

/** 获取量表分类列表 */
export function getScaleCategoryList() {
  return httpClient.get<Result<ScaleCategoryVO[]>>("/user/scale/category");
}
