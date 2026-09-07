import httpClient from "@shared/api/instance";
import type { Result } from "@shared/api/types";

/** 获取管理员资料 */
export function getProfile() {
  return httpClient.get<Result<any>>("/admin/profile/profile");
}

/** 更新管理员资料 */
export function updateProfile(data: any) {
  return httpClient.put<Result<void>>("/admin/profile/profileUpdate", data);
}