import httpClient from "@shared/api/instance";
import type { Result } from "@shared/api/types";

/** 登录用户简要信息（登录后返回） */
export interface LoginUserInfoVO {
  id: number;
  loginAccount: string;
  username: string;
  avatar?: string;
}

/** 用户登录结果 */
export interface LoginResultVO {
  token: string;
  headerName?: string;
  roles?: string[];
  userInfo?: LoginUserInfoVO | null;
}

/** 用户注册 */
export function register(data: {
  loginAccount: string;
  username: string;
  password: string;
  mobile?: string;
  email?: string;
  key: string;
  code: string;
}) {
  return httpClient.post<Result<void>>("/user/account/register", data);
}

/** 用户登录 */
export function login(data: { loginAccount: string; password: string; key: string; code: string }) {
  return httpClient.post<Result<LoginResultVO>>("/user/account/login", data);
}

/** 用户登出 */
export function logout() {
  return httpClient.post<Result<void>>("/user/account/logout");
}

/** 修改密码请求体 */
export interface PasswordDTO {
  originalPassword: string;
  password: string;
  email?: string;
  code?: string;
}

/** 用户修改密码 */
export function pwdUpdate(data: PasswordDTO) {
  return httpClient.patch<Result<void>>("/user/account", data);
}
