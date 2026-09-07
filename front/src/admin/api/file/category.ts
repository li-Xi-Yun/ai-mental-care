import httpClient from "@shared/api/instance";
import type { Result } from "@shared/api/types";

/** 新增文件分类 */
export function addCategory(data: any) {
  return httpClient.post<Result<any>>("/admin/file/category", data);
}

/** 更新文件分类 */
export function updateCategory(data: any) {
  return httpClient.put<Result<void>>("/admin/file/category", data);
}

/** 删除文件分类 */
export function deleteCategory(categoryId: string) {
  return httpClient.delete<Result<void>>(`/admin/file/category/${categoryId}`);
}

/** 获取子分类 */
export function getSubCategories(categoryId: string) {
  return httpClient.get<Result<any[]>>(`/admin/file/category/sub/${categoryId}`);
}

/** 获取分类树 */
export function getCategoryTree() {
  return httpClient.get<Result<any[]>>("/admin/file/category");
}

/** 获取分类详情 */
export function getCategoryDetail(categoryId: string) {
  return httpClient.get<Result<any>>(`/admin/file/category/${categoryId}`);
}