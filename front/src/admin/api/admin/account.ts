import httpClient from "@shared/api/instance";
import type { Result } from "@shared/api/types";

/** 管理员注册 */
export function register(data: { username: string; password: string }) {
  return httpClient.post<Result<void>>("/admin/account/register", data);
}

/** 管理员登录 */
export function login(data: { username: string; password: string }) {
  return httpClient.post<Result<{ token: string }>>("/admin/account/login", data);
}

/** 管理员登出 */
export function logout() {
  return httpClient.post<Result<void>>("/admin/account/logout");
}