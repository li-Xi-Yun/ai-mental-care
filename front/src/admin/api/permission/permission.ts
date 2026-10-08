import httpClient from "@shared/api/instance";
import type { Result } from "@shared/api/types";

/** 查询权限树形列表 */
export function getPermissionTree() {
  return httpClient.get<Result<any[]>>("/admin/permission/list");
}

/** 查询单个权限详情 */
export function getPermissionDetail(id: number) {
  return httpClient.get<Result<any>>(`/admin/permission/${id}`);
}

/** 新增权限 */
export function addPermission(data: any) {
  return httpClient.post<Result<void>>("/admin/permission/add", data);
}

/** 修改权限 */
export function updatePermission(id: number, data: any) {
  return httpClient.put<Result<void>>(`/admin/permission/update/${id}`, data);
}

/** 删除权限 */
export function deletePermission(id: number) {
  return httpClient.delete<Result<void>>(`/admin/permission/${id}`);
}
