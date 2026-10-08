import httpClient from "@shared/api/instance";
import type { Result } from "@shared/api/types";
import type { AdminScaleOptionItemDTO } from "./types";

/** 批量新增选项 */
export function addScaleOptions(data: { questionId: number; options: AdminScaleOptionItemDTO[] }) {
  return httpClient.post<Result<void>>("/admin/scale/option/batch", data);
}

/** 修改单个选项 */
export function updateScaleOption(optionId: number | string, data: AdminScaleOptionItemDTO) {
  return httpClient.put<Result<void>>(`/admin/scale/option/${optionId}`, data);
}

/** 批量调整选项顺序 */
export function updateScaleOptionSort(sortItems: Array<{ optionId: number; sort: number }>) {
  return httpClient.put<Result<void>>("/admin/scale/option/sort", sortItems);
}

/** 批量删除选项 */
export function deleteScaleOptions(data: { questionId: number; optionIds: number[] }) {
  return httpClient.delete<Result<void>>("/admin/scale/option/batch", { data });
}
