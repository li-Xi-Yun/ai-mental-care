import httpClient from "../instance";
import type { Result } from "../types";

/** 验证码图片响应（Base64 图片 + 校验用的 key） */
export interface CodeImageVO {
  image: string;
  key: string;
}

/** 获取登录验证码图片（POST /common/verification/code，返回 { image, key }） */
export function getCode() {
  return httpClient.post<Result<CodeImageVO>>("/common/verification/code");
}

/** 发送验证码 */
export function sendVerificationCode(phone: string) {
  return httpClient.post<Result<void>>("/common/verification/code", { phone });
}

/** 发送邮箱验证码 */
export function sendEmailCode(email: string) {
  return httpClient.post<Result<void>>("/common/verification/email", { email });
}