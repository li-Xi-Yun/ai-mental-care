import httpClient from "@shared/api/instance";
import type { Result } from "@shared/api/types";

/** 获取用户资料 */
export function getProfile() {
  return httpClient.get<Result<any>>("/user/profile/profile");
}

/** 更新用户资料 */
export function updateProfile(data: any) {
  return httpClient.put<Result<void>>("/user/profile/profileUpdate", data);
}