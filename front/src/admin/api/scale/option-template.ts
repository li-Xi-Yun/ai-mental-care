import httpClient from "@shared/api/instance";
import type { Result } from "@shared/api/types";

/* ==================== 选项模板（option-template） ==================== */

/** 选项模板项 DTO */
export interface AdminScaleOptionTemplateItemDTO {
  optionText: string;
  score?: number | null;
  sort?: number;
}

/** 选项模板项 VO */
export interface AdminScaleOptionTemplateItemVO {
  id?: number;
  templateGroupId?: number;
  optionText: string;
  score?: number | null;
  sort?: number;
  createdTime?: string;
  updatedTime?: string;
}

/** 选项模板组新增/修改 DTO */
export interface AdminScaleOptionTemplateDTO {
  id?: number;
  scaleVersionId: number;
  templateName: string;
  templateDesc?: string;
  items: AdminScaleOptionTemplateItemDTO[];
}

/** 选项模板组 VO */
export interface AdminScaleOptionTemplateVO {
  id: number;
  scaleVersionId?: number;
  templateName: string;
  templateDesc?: string | null;
  items?: AdminScaleOptionTemplateItemVO[];
  createdTime?: string;
  updatedTime?: string;
  deletedFlag?: number;
}

/** 选项模板应用到题目 DTO */
export interface AdminScaleOptionTemplateApplyDTO {
  questionIds: number[];
  mode?: string;
}

/** 选项模板复制 DTO */
export interface AdminScaleOptionTemplateCopyDTO {
  groupId: number;
  targetScaleVersionId: number;
}

/** 查询选项模板列表（按 scaleVersionId） */
export function getOptionTemplateList(scaleVersionId: number | string) {
  return httpClient.get<Result<AdminScaleOptionTemplateVO[]>>("/admin/scale/option/template/list", {
    params: { scaleVersionId },
  });
}

/** 新增选项模板 */
export function addOptionTemplate(data: AdminScaleOptionTemplateDTO) {
  return httpClient.post<Result<void>>("/admin/scale/option/template", data);
}

/** 修改选项模板 */
export function updateOptionTemplate(groupId: number | string, data: AdminScaleOptionTemplateDTO) {
  return httpClient.put<Result<void>>(`/admin/scale/option/template/${groupId}`, data);
}

/** 应用模板到题目（REPLACE=覆盖写 APPEND=追加写） */
export function applyOptionTemplate(groupId: number | string, data: AdminScaleOptionTemplateApplyDTO) {
  return httpClient.put<Result<void>>(`/admin/scale/option/template/${groupId}/apply`, data);
}

/** 复制模板到目标版本 */
export function copyOptionTemplate(data: AdminScaleOptionTemplateCopyDTO) {
  return httpClient.post<Result<void>>("/admin/scale/option/template/copy", data);
}

/** 删除选项模板 */
export function deleteOptionTemplate(groupId: number | string) {
  return httpClient.delete<Result<void>>(`/admin/scale/option/template/${groupId}`);
}
