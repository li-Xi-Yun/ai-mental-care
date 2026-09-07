import httpClient from "@shared/api/instance";
import type { Result } from "@shared/api/types";

/** 用户注册 */
export function register(data: { username: string; password: string; phone?: string; email?: string }) {
  return httpClient.post<Result<void>>("/user/account/register", data);
}

/** 用户登录 */
export function login(data: { username: string; password: string }) {
  return httpClient.post<Result<{ token: string }>>("/user/account/login", data);
}

/** 用户登出 */
export function logout() {
  return httpClient.post<Result<void>>("/user/account/logout");
}