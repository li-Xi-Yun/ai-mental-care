import httpClient from "@shared/api/instance";
import type { Result } from "@shared/api/types";

/** 管理员个人资料 */
export interface AdminProfileVO {
  id: number;
  loginAccount: string;
  username: string;
  mobile: string;
  email: string;
  status: number;
  banReason?: string;
  banTime?: string;
  banEndTime?: string;
  createdTime?: string;
}

/** 管理员资料更新请求体（mobile/email 为空时不提交，保持原值） */
export interface AdminProfileDTO {
  username: string;
  mobile?: string;
  email?: string;
}

/** 获取管理员资料 */
export function getProfile() {
  return httpClient.get<Result<AdminProfileVO>>("/admin/profile/profile");
}

/** 更新管理员资料 */
export function updateProfile(data: AdminProfileDTO) {
  return httpClient.put<Result<void>>("/admin/profile/profileUpdate", data);
}