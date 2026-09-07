import httpClient from "@shared/api/instance";
import type { Result } from "@shared/api/types";

/** 新增选项模板 */
export function addOptionTemplate(data: any) {
  return httpClient.post<Result<any>>("/admin/scale/option/template", data);
}

/** 获取选项模板列表 */
export function getOptionTemplateList() {
  return httpClient.get<Result<any[]>>("/admin/scale/option/template");
}

/** 更新选项模板 */
export function updateOptionTemplate(data: any) {
  return httpClient.put<Result<void>>("/admin/scale/option/template", data);
}

/** 删除选项模板 */
export function deleteOptionTemplate(templateId: string) {
  return httpClient.delete<Result<void>>(`/admin/scale/option/template/${templateId}`);
}