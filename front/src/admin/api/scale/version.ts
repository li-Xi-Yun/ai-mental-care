import httpClient from "@shared/api/instance";
import type { Result } from "@shared/api/types";
import type { AdminScaleVersionCopyDTO, AdminScaleVersionDTO, AdminScaleVersionVO } from "./types";

/** 查询版本列表 */
export function getScaleVersionList(scaleId: number | string) {
  return httpClient.get<Result<AdminScaleVersionVO[]>>("/admin/scale/version/list", { params: { scaleId } });
}

/** 获取版本详情 */
export function getScaleVersionDetail(versionId: number | string) {
  return httpClient.get<Result<AdminScaleVersionVO>>(`/admin/scale/version/${versionId}`);
}

/** 新增空版本 */
export function addScaleVersion(data: AdminScaleVersionDTO) {
  return httpClient.post<Result<void>>("/admin/scale/version", data);
}

/** 复制版本 */
export function copyScaleVersion(data: AdminScaleVersionCopyDTO) {
  return httpClient.post<Result<void>>("/admin/scale/version/copy", data);
}

/** 修改版本信息 */
export function updateScaleVersion(versionId: number | string, data: AdminScaleVersionDTO) {
  return httpClient.put<Result<void>>(`/admin/scale/version/${versionId}`, data);
}

/** 发布版本（设为当前生效版本） */
export function publishScaleVersion(versionId: number | string) {
  return httpClient.put<Result<void>>(`/admin/scale/version/${versionId}/publish`);
}

/** 删除版本 */
export function deleteScaleVersion(versionId: number | string) {
  return httpClient.delete<Result<void>>(`/admin/scale/version/${versionId}`);
}
