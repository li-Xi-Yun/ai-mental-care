import httpClient from "@shared/api/instance";
import type { Result, ListResult } from "@shared/api/types";

/** 分页查询角色列表 */
export function getRolePage(data: any) {
  return httpClient.post<ListResult<any>>("/admin/sysRole/page", data);
}

/** 查询角色详情 */
export function getRoleDetail(id: number) {
  return httpClient.get<Result<any>>(`/admin/sysRole/${id}`);
}

/** 新增角色 */
export function addRole(data: any) {
  return httpClient.post<Result<void>>("/admin/sysRole/add", data);
}

/** 修改角色 */
export function updateRole(id: number, data: any) {
  return httpClient.put<Result<void>>(`/admin/sysRole/update/${id}`, data);
}

/** 修改角色状态 */
export function updateRoleStatus(id: number, status: number) {
  return httpClient.put<Result<void>>(`/admin/sysRole/status/${id}`, null, { params: { status } });
}

/** 删除角色 */
export function deleteRole(id: number) {
  return httpClient.delete<Result<void>>(`/admin/sysRole/${id}`);
}

/** 查询角色绑定的权限集合 */
export function getRolePermissions(id: number) {
  return httpClient.get<Result<any[]>>(`/admin/sysRole/${id}/permissions`);
}

/** 角色分配权限 */
export function assignRolePermissions(id: number, permissionIds: number[]) {
  return httpClient.put<Result<void>>(`/admin/sysRole/${id}/assignPermissions`, null, { params: { permissionIds } });
}

/** 获取全部正常角色列表（下拉选择） */
export function getAllSimpleRoles() {
  return httpClient.get<Result<any[]>>("/admin/sysRole/allSimple");
}
