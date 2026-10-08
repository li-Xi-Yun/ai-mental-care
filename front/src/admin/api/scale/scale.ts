import httpClient from "@shared/api/instance";
import type { Result, ListResult } from "@shared/api/types";

/** 分页查询量表主表 */
export function getScalePage(data: any) {
  return httpClient.post<ListResult<any>>("/admin/scale/page", data);
}

/** 获取量表详情 */
export function getScaleDetail(scaleId: string | number) {
  return httpClient.get<Result<any>>(`/admin/scale/${scaleId}`);
}

/** 新增量表 */
export function addScale(data: any) {
  return httpClient.post<Result<any>>("/admin/scale", data);
}

/** 更新量表 */
export function updateScale(scaleId: string | number, data: any) {
  return httpClient.put<Result<void>>(`/admin/scale/${scaleId}`, data);
}

/** 启用/禁用量表（body：{ status: 0|1 }） */
export function updateScaleStatus(scaleId: string | number, status: number) {
  return httpClient.put<Result<void>>(`/admin/scale/${scaleId}/status`, { status });
}

/** 删除量表（逻辑删除，级联） */
export function deleteScale(scaleId: string | number) {
  return httpClient.delete<Result<void>>(`/admin/scale/${scaleId}`);
}

/** 恢复量表 */
export function restoreScale(scaleId: string | number) {
  return httpClient.put<Result<void>>(`/admin/scale/${scaleId}/restore`);
}
