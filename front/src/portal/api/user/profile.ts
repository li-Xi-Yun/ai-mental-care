import httpClient from "@shared/api/instance";
import type { Result } from "@shared/api/types";

/** 用户资料展示 */
export interface UserProfileVO {
  id?: number;
  /** 账号名 */
  loginAccount?: string;
  /** 用户名 / 昵称 */
  username?: string;
  /** 头像 URL */
  avatar?: string;
  /** 手机号 */
  mobile?: string;
  /** 邮箱 */
  email?: string;
  /** 个人简介 */
  introduction?: string;
  /** 性别 0：女 1：男 2：未知 */
  gender?: number;
  /** 账号状态 */
  status?: number;
  /** 注册时间 */
  createdTime?: string;
}

/** 用户资料更新请求体 */
export interface UserProfileUpdateDTO {
  username?: string;
  loginAccount?: string;
  avatar?: string;
  mobile?: string;
  email?: string;
  introduction?: string;
  gender?: number;
}

/** 获取用户资料 */
export function getProfile() {
  return httpClient.get<Result<UserProfileVO>>("/user/profile/profile");
}

/** 更新用户资料 */
export function updateProfile(data: UserProfileUpdateDTO) {
  return httpClient.put<Result<void>>("/user/profile/profileUpdate", data);
}