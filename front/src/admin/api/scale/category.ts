import httpClient from "@shared/api/instance";
import type { Result, ListResult } from "@shared/api/types";

/** 分页查询量表类别 */
export function getScaleCategoryPage(data: any) {
  return httpClient.post<ListResult<any>>("/admin/scale/category/page", data);
}

/** 新增量表类别 */
export function addScaleCategory(data: any) {
  return httpClient.post<Result<any>>("/admin/scale/category", data);
}

/** 修改量表类别 */
export function updateScaleCategory(categoryId: string | number, data: any) {
  return httpClient.put<Result<void>>(`/admin/scale/category/${categoryId}`, data);
}

/** 调整量表类别排序（@RequestParam sort） */
export function updateScaleCategorySort(categoryId: string | number, sort: number) {
  return httpClient.put<Result<void>>(`/admin/scale/category/${categoryId}/sort`, null, { params: { sort } });
}

/** 删除量表类别（该类别下存在未删除量表时禁止删除） */
export function deleteScaleCategory(categoryId: string | number) {
  return httpClient.delete<Result<void>>(`/admin/scale/category/${categoryId}`);
}