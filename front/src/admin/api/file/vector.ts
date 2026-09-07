import httpClient from "@shared/api/instance";
import type { Result } from "@shared/api/types";

/** 加载文件向量 */
export function loadFileVector(fileId: string) {
  return httpClient.post<Result<any>>(`/admin/file/vector/${fileId}/load`);
}

/** 删除文件向量 */
export function deleteFileVector(fileId: string) {
  return httpClient.delete<Result<void>>(`/admin/file/vector/${fileId}`);
}

/** 获取文件向量信息 */
export function getFileVector(fileId: string) {
  return httpClient.get<Result<any>>(`/admin/file/vector/${fileId}`);
}

/** 中断文件向量加载 */
export function interruptFileVector(fileId: string, data: any) {
  return httpClient.post<Result<void>>(`/admin/file/vector/${fileId}/interrupt`, data);
}