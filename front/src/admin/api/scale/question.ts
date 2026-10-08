import httpClient from "@shared/api/instance";
import type { Result } from "@shared/api/types";
import type { AdminScaleQuestionDTO, AdminScaleQuestionVO } from "./types";

/** 查询题目列表 */
export function getScaleQuestionList(scaleVersionId: number | string) {
  return httpClient.get<Result<AdminScaleQuestionVO[]>>("/admin/scale/question/list", {
    params: { scaleVersionId },
  });
}

/** 获取题目详情 */
export function getScaleQuestionDetail(questionId: number | string) {
  return httpClient.get<Result<AdminScaleQuestionVO>>(`/admin/scale/question/${questionId}`);
}

/** 新增题目（可内联选项） */
export function addScaleQuestion(data: AdminScaleQuestionDTO) {
  return httpClient.post<Result<void>>("/admin/scale/question", data);
}

/** 修改题目（可整体替换选项） */
export function updateScaleQuestion(questionId: number | string, data: AdminScaleQuestionDTO) {
  return httpClient.put<Result<void>>(`/admin/scale/question/${questionId}`, data);
}

/** 批量调整题目顺序 */
export function updateScaleQuestionSort(sortItems: Array<{ questionId: number; sort: number }>) {
  return httpClient.put<Result<void>>("/admin/scale/question/sort", sortItems);
}

/** 调整题目所属维度 */
export function updateScaleQuestionDimension(questionId: number | string, dimensionId: number | null) {
  return httpClient.put<Result<void>>(`/admin/scale/question/${questionId}/dimension`, null, {
    params: dimensionId == null ? {} : { dimensionId },
  });
}

/** 复制题目到指定版本 */
export function copyScaleQuestion(questionId: number | string, targetScaleVersionId: number | string) {
  return httpClient.put<Result<void>>(`/admin/scale/question/${questionId}/copy`, null, {
    params: { targetScaleVersionId },
  });
}

/** 删除题目 */
export function deleteScaleQuestion(questionId: number | string) {
  return httpClient.delete<Result<void>>(`/admin/scale/question/${questionId}`);
}