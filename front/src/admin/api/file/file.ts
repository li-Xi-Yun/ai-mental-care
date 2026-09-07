import httpClient from "@shared/api/instance";
import type { Result, ListResult } from "@shared/api/types";

/** 上传文件 */
export function uploadFile(data: FormData) {
  return httpClient.post<Result<any>>("/admin/file/upload", data, {
    headers: { "Content-Type": "multipart/form-data" },
  });
}

/** 获取文件分页列表 */
export function getFilePage(data: any) {
  return httpClient.post<ListResult<any>>("/admin/file/page", data);
}

/** 删除文件 */
export function deleteFile(fileId: string) {
  return httpClient.delete<Result<void>>(`/admin/file/${fileId}`);
}

/** 更新文件信息 */
export function updateFile(fileId: string, data: any) {
  return httpClient.put<Result<void>>(`/admin/file/${fileId}`, data);
}