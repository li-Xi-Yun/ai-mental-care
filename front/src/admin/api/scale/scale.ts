import httpClient from "@shared/api/instance";
import type { Result, ListResult } from "@shared/api/types";

/** 新增量表 */
export function addScale(data: any) {
  return httpClient.post<Result<any>>("/admin/scale", data);
}

/** 获取量表分页列表 */
export function getScalePage(data: any) {
  return httpClient.post<ListResult<any>>("/admin/scale/list", data);
}

/** 删除量表 */
export function deleteScale(scaleId: string) {
  return httpClient.delete<Result<void>>(`/admin/scale/${scaleId}`);
}

/** 更新量表 */
export function updateScale(scaleId: string, data: any) {
  return httpClient.put<Result<void>>(`/admin/scale/${scaleId}`, data);
}

/** 恢复量表 */
export function restoreScale(scaleId: string) {
  return httpClient.put<Result<void>>(`/admin/scale/${scaleId}/restore`);
}