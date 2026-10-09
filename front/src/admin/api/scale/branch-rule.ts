import httpClient from "@shared/api/instance";
import type { Result } from "@shared/api/types";
import type { AdminScaleBranchRuleBatchDTO, AdminScaleBranchRuleDTO, AdminScaleBranchRuleVO } from "./types";

/** 查询跳题规则列表（按 scaleVersionId） */
export function getScaleBranchRuleList(scaleVersionId: number | string) {
  return httpClient.get<Result<AdminScaleBranchRuleVO[]>>("/admin/scale/branch-rule/list", {
    params: { scaleVersionId },
  });
}

/** 新增单条跳题规则 */
export function addScaleBranchRule(data: AdminScaleBranchRuleDTO) {
  return httpClient.post<Result<void>>("/admin/scale/branch-rule", data);
}

/** 批量保存跳题规则（整体维护） */
export function saveScaleBranchRuleBatch(data: AdminScaleBranchRuleBatchDTO) {
  return httpClient.post<Result<void>>("/admin/scale/branch-rule/batch", data);
}

/** 修改单条跳题规则 */
export function updateScaleBranchRule(ruleId: number | string, data: AdminScaleBranchRuleDTO) {
  return httpClient.put<Result<void>>(`/admin/scale/branch-rule/${ruleId}`, data);
}

/** 删除单条跳题规则 */
export function deleteScaleBranchRule(ruleId: number | string) {
  return httpClient.delete<Result<void>>(`/admin/scale/branch-rule/${ruleId}`);
}