import httpClient from "@shared/api/instance";
import type { Result } from "@shared/api/types";

/** 批量新增量表选项 */
export function addScaleOptions(data: any) {
  return httpClient.post<Result<any>>("/admin/scale/option", data);
}

/** 删除量表选项 */
export function deleteScaleOptions(data: any) {
  return httpClient.delete<Result<void>>("/admin/scale/option", { data });
}

/** 更新量表选项 */
export function updateScaleOption(optionId: string, data: any) {
  return httpClient.put<Result<void>>(`/admin/scale/option/update/${optionId}`, data);
}