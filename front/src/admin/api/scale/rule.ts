import httpClient from "@shared/api/instance";
import type { Result } from "@shared/api/types";

/** 新增量表规则 */
export function addScaleRule(data: any) {
  return httpClient.post<Result<any>>("/admin/scale/rule", data);
}

/** 获取量表规则列表 */
export function getScaleRuleList() {
  return httpClient.get<Result<any[]>>("/admin/scale/rule");
}

/** 更新量表规则 */
export function updateScaleRule(ruleId: string, data: any) {
  return httpClient.put<Result<void>>(`/admin/scale/rule/${ruleId}`, data);
}

/** 删除量表规则 */
export function deleteScaleRule(ruleId: string) {
  return httpClient.delete<Result<void>>(`/admin/scale/rule/${ruleId}`);
}