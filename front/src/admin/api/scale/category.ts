import httpClient from "@shared/api/instance";
import type { Result } from "@shared/api/types";

/** 获取量表分类列表 */
export function getScaleCategoryList() {
  return httpClient.get<Result<any[]>>("/admin/scale/category");
}

/** 新增量表分类 */
export function addScaleCategory(data: any) {
  return httpClient.post<Result<any>>("/admin/scale/category", data);
}

/** 删除量表分类 */
export function deleteScaleCategory(categoryId: string) {
  return httpClient.delete<Result<void>>(`/admin/scale/category/${categoryId}`);
}

/** 更新量表分类 */
export function updateScaleCategory(categoryId: string, data: any) {
  return httpClient.put<Result<void>>(`/admin/scale/category/${categoryId}`, data);
}