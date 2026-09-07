import httpClient from "@shared/api/instance";
import type { Result, ListResult } from "@shared/api/types";

/** 获取用户列表 */
export function getUserList(data: any) {
  return httpClient.post<ListResult<any>>("/admin/user/list", data);
}

/** 获取管理员列表 */
export function getAdminList(data: any) {
  return httpClient.post<ListResult<any>>("/admin/user/admin/list", data);
}