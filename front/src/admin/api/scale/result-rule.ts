import httpClient from "@shared/api/instance";
import type { Result } from "@shared/api/types";
import type { AdminScaleResultRuleBatchDTO, AdminScaleResultRuleDTO, AdminScaleResultRuleVO } from "./types";

/** 查询结果规则列表（按 scaleVersionId） */
export function getScaleResultRuleList(scaleVersionId: number | string) {
  return httpClient.get<Result<AdminScaleResultRuleVO[]>>("/admin/scale/result-rule/list", {
    params: { scaleVersionId },
  });
}

/** 新增单条结果规则 */
export function addScaleResultRule(data: AdminScaleResultRuleDTO) {
  return httpClient.post<Result<void>>("/admin/scale/result-rule", data);
}

/** 批量保存结果规则（整体维护） */
export function saveScaleResultRuleBatch(data: AdminScaleResultRuleBatchDTO) {
  return httpClient.post<Result<void>>("/admin/scale/result-rule/batch", data);
}

/** 修改单条结果规则 */
export function updateScaleResultRule(ruleId: number | string, data: AdminScaleResultRuleDTO) {
  return httpClient.put<Result<void>>(`/admin/scale/result-rule/${ruleId}`, data);
}

/** 删除单条结果规则 */
export function deleteScaleResultRule(ruleId: number | string) {
  return httpClient.delete<Result<void>>(`/admin/scale/result-rule/${ruleId}`);
}