import httpClient from "@shared/api/instance";
import type { Result } from "@shared/api/types";

/** 提交量表答案 */
export function submitScaleAnswer(data: any) {
  return httpClient.post<Result<any>>("/user/scale/user", data);
}

/** 获取量表作答记录 */
export function getScaleRecords() {
  return httpClient.get<Result<any[]>>("/user/scale/user/records");
}

/** 获取作答记录详情 */
export function getScaleRecordDetail(recordId: string) {
  return httpClient.get<Result<any>>(`/user/scale/user/records/${recordId}/details`);
}