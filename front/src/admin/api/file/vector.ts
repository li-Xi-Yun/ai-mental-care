import httpClient from "@shared/api/instance";
import type { PageResult, Result } from "@shared/api/types";

/** 文件向量分片信息 */
export interface FileVectorVO {
  id: number;
  fileId: number;
  content: string;
  chunkLevel1Idx: number;
  chunkLevel2Idx: number;
  createdTime?: string;
}

/** 文件向量分页查询参数 */
export interface FileVectorQuery {
  pageNum?: number;
  pageSize?: number;
}

/** 加载文件向量 */
export function loadFileVector(fileId: string) {
  return httpClient.post<Result<void>>(`/admin/file/vector/${fileId}/load`);
}

/** 删除文件向量 */
export function deleteFileVector(fileId: string) {
  return httpClient.delete<Result<void>>(`/admin/file/vector/${fileId}`);
}

/** 获取文件向量信息（分页） */
export function getFileVector(fileId: string, params?: FileVectorQuery) {
  return httpClient.get<Result<PageResult<FileVectorVO>>>(`/admin/file/vector/${fileId}`, { params });
}

/** 中断文件向量加载 */
export function interruptFileVector(fileId: string, data?: unknown) {
  return httpClient.post<Result<void>>(`/admin/file/vector/${fileId}/interrupt`, data);
}