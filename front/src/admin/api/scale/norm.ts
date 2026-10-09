import httpClient from "@shared/api/instance";
import type { Result } from "@shared/api/types";
import type {
  AdminScaleNormBatchDTO,
  AdminScaleNormDTO,
  AdminScaleNormDeleteDTO,
  AdminScaleNormGroupDTO,
  AdminScaleNormGroupVO,
  AdminScaleNormVO,
} from "./types";

/** 查询常模组列表（按 scaleVersionId） */
export function getScaleNormGroupList(scaleVersionId: number | string) {
  return httpClient.get<Result<AdminScaleNormGroupVO[]>>("/admin/scale/norm/group/list", {
    params: { scaleVersionId },
  });
}

/** 获取常模组详情（含常模明细） */
export function getScaleNormGroupDetail(groupId: number | string) {
  return httpClient.get<Result<AdminScaleNormGroupVO>>(`/admin/scale/norm/group/${groupId}`);
}

/** 新增常模组 */
export function addScaleNormGroup(data: AdminScaleNormGroupDTO) {
  return httpClient.post<Result<void>>("/admin/scale/norm/group", data);
}

/** 修改常模组 */
export function updateScaleNormGroup(groupId: number | string, data: AdminScaleNormGroupDTO) {
  return httpClient.put<Result<void>>(`/admin/scale/norm/group/${groupId}`, data);
}

/** 删除常模组（级联删除常模明细） */
export function deleteScaleNormGroup(groupId: number | string) {
  return httpClient.delete<Result<void>>(`/admin/scale/norm/group/${groupId}`);
}

/** 查询常模明细列表（按 normGroupId） */
export function getScaleNormList(normGroupId: number | string) {
  return httpClient.get<Result<AdminScaleNormVO[]>>("/admin/scale/norm/list", {
    params: { normGroupId },
  });
}

/** 批量保存常模明细（按唯一键 upsert） */
export function saveScaleNormBatch(data: AdminScaleNormBatchDTO) {
  return httpClient.post<Result<void>>("/admin/scale/norm/batch", data);
}

/** 修改单条常模明细 */
export function updateScaleNorm(normId: number | string, data: AdminScaleNormDTO) {
  return httpClient.put<Result<void>>(`/admin/scale/norm/${normId}`, data);
}

/** 批量删除常模明细 */
export function deleteScaleNorms(data: AdminScaleNormDeleteDTO) {
  return httpClient.delete<Result<void>>("/admin/scale/norm/batch", { data });
}