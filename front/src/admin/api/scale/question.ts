import httpClient from "@shared/api/instance";
import type { Result } from "@shared/api/types";

/** 新增量表题目 */
export function addScaleQuestion(data: any) {
  return httpClient.post<Result<any>>("/admin/scale/questions", data);
}

/** 获取量表题目列表 */
export function getScaleQuestionList(data: any) {
  return httpClient.get<Result<any[]>>("/admin/scale/questions/list", { params: data });
}

/** 删除量表题目 */
export function deleteScaleQuestion(questionId: string) {
  return httpClient.delete<Result<void>>(`/admin/scale/questions/${questionId}`);
}

/** 更新量表题目 */
export function updateScaleQuestion(questionId: string, data: any) {
  return httpClient.put<Result<void>>(`/admin/scale/questions/${questionId}`, data);
}