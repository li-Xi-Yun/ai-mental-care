import httpClient from "@shared/api/instance";
import type { Result, ListResult } from "@shared/api/types";

/** 分页查询前台用户临时权限人员 */
export function pageUserTempPermissions(data: any) {
  return httpClient.post<ListResult<any>>("/admin/tempPerm/user/page", data);
}

/** 分页查询后台管理员临时权限人员 */
export function pageAdminTempPermissions(data: any) {
  return httpClient.post<ListResult<any>>("/admin/tempPerm/admin/page", data);
}

/** 查询前台用户全部临时权限 */
export function listUserTempPermissions(personId: number, status?: number) {
  return httpClient.get<Result<any[]>>(`/admin/tempPerm/user/${personId}`, { params: { status } });
}

/** 查询后台管理员全部临时权限 */
export function listAdminTempPermissions(personId: number, status?: number) {
  return httpClient.get<Result<any[]>>(`/admin/tempPerm/admin/${personId}`, { params: { status } });
}

/** 授予临时权限 */
export function grantTempPermission(data: any) {
  return httpClient.post<Result<void>>("/admin/tempPerm/grant", data);
}

/** 手动作废临时权限 */
export function revokeTempPermission(id: number) {
  return httpClient.put<Result<void>>(`/admin/tempPerm/${id}/revoke`);
}
