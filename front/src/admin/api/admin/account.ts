import httpClient from "@shared/api/instance";
import type { Result } from "@shared/api/types";

/** 管理员简要信息（登录后返回） */
export interface AdminLoginUserInfoVO {
  id: number;
  loginAccount: string;
  username: string;
}

/** 管理员登录结果 */
export interface AdminLoginResultVO {
  token: string;
  headerName: string;
  roles: string[];
  adminInfo: AdminLoginUserInfoVO | null;
}

/** 管理员注册 */
export function register(data: { username: string; password: string }) {
  return httpClient.post<Result<void>>("/admin/account/register", data);
}

/** 管理员登录 */
export function login(data: {
  loginAccount: string;
  password: string;
  key: string;
  code: string;
}) {
  return httpClient.post<Result<AdminLoginResultVO>>("/admin/account/login", data);
}

/** 管理员登出 */
export function logout() {
  return httpClient.post<Result<void>>("/admin/account/logout");
}

/** 管理员修改密码（PATCH /admin/account，PasswordDTO { originalPassword, password }） */
export function pwdUpdate(data: { originalPassword: string; password: string }) {
  return httpClient.patch<Result<void>>("/admin/account", data);
}