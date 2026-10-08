import httpClient from "@shared/api/instance";
import type { Result } from "@shared/api/types";
import type { AdminScaleDimensionDTO, AdminScaleDimensionVO } from "./types";

/** 查询维度列表 */
export function getScaleDimensionList(scaleVersionId: number | string) {
  return httpClient.get<Result<AdminScaleDimensionVO[]>>("/admin/scale/dimension/list", {
    params: { scaleVersionId },
  });
}

/** 新增维度 */
export function addScaleDimension(data: AdminScaleDimensionDTO) {
  return httpClient.post<Result<void>>("/admin/scale/dimension", data);
}

/** 修改维度 */
export function updateScaleDimension(dimensionId: number | string, data: AdminScaleDimensionDTO) {
  return httpClient.put<Result<void>>(`/admin/scale/dimension/${dimensionId}`, data);
}

/** 调整维度排序 */
export function updateScaleDimensionSort(dimensionId: number | string, sort: number) {
  return httpClient.put<Result<void>>(`/admin/scale/dimension/${dimensionId}/sort`, null, { params: { sort } });
}

/** 删除维度 */
export function deleteScaleDimension(dimensionId: number | string) {
  return httpClient.delete<Result<void>>(`/admin/scale/dimension/${dimensionId}`);
}
