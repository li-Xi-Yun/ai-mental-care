import httpClient from "../instance";
import type { Result } from "../types";

/** 发送验证码 */
export function sendVerificationCode(phone: string) {
  return httpClient.post<Result<void>>("/common/verification/code", { phone });
}

/** 发送邮箱验证码 */
export function sendEmailCode(email: string) {
  return httpClient.post<Result<void>>("/common/verification/email", { email });
}