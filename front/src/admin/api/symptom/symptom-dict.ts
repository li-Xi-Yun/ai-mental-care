import httpClient from "@shared/api/instance";
import type { Result, ListResult } from "@shared/api/types";

/** 获取症状字典分页列表 */
export function getSymptomDictPage(data: any) {
  return httpClient.post<ListResult<any>>("/admin/symptom-dict/page", data);
}

/** 获取症状字典详情 */
export function getSymptomDictDetail(id: string) {
  return httpClient.get<Result<any>>(`/admin/symptom-dict/${id}`);
}

/** 新增症状字典 */
export function addSymptomDict(data: any) {
  return httpClient.post<Result<any>>("/admin/symptom-dict", data);
}

/** 更新症状字典 */
export function updateSymptomDict(id: string, data: any) {
  return httpClient.put<Result<void>>(`/admin/symptom-dict/${id}`, data);
}

/** 更新症状字典状态 */
export function updateSymptomDictStatus(id: string, data: any) {
  return httpClient.put<Result<void>>(`/admin/symptom-dict/${id}/status`, data);
}

/** 批量删除症状字典 */
export function batchDeleteSymptomDict(ids: string[]) {
  return httpClient.delete<Result<void>>("/admin/symptom-dict/batch", { data: { ids } });
}

/** 获取症状字典选项 */
export function getSymptomDictOptions() {
  return httpClient.get<Result<any[]>>("/admin/symptom-dict/options");
}