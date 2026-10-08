import httpClient from "../instance";
import type { Result } from "../types";

/** 上传图片（POST /common/upload/image） */
export function uploadImage(file: File) {
  const formData = new FormData();
  formData.append("file", file);
  return httpClient.post<Result<string>>("/common/upload/image", formData, {
    headers: { "Content-Type": "multipart/form-data" },
  });
}