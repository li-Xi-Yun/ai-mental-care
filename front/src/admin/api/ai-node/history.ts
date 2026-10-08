import httpClient from "@shared/api/instance";
import type { Result, PageResult } from "@shared/api/types";

/** AI节点配置变更历史 VO（对应后端 AiNodeConfigHistoryVO） */
export interface AiNodeConfigHistoryVO {
  id?: number | null;
  configId?: number | null;
  nodeKey?: string | null;
  oldSystemPrompt?: string | null;
  newSystemPrompt?: string | null;
  oldModelType?: number | null;
  newModelType?: number | null;
  oldParams?: Record<string, unknown> | null;
  newParams?: Record<string, unknown> | null;
  oldVersion?: number | null;
  newVersion?: number | null;
  changeSummary?: string | null;
  createdTime?: string | null;
  createdBy?: number | null;
  createdByName?: string | null;
}

/** 变更历史分页查询入参（对应后端 AiNodeHistoryQueryDTO） */
export interface AiNodeHistoryQueryDTO {
  pageNum: number;
  pageSize: number;
  nodeKey?: string | null;
  changeSummary?: string | null;
}

/** 分页查询AI节点配置变更历史 */
export function getAiNodeHistoryPage(id: string | number, data: AiNodeHistoryQueryDTO) {
  return httpClient.post<Result<PageResult<AiNodeConfigHistoryVO>>>(`/admin/ai-node-history/${id}/page`, data);
}

/** 获取变更历史详情（含 old/new 对比快照） */
export function getAiNodeHistoryDetail(historyId: string | number) {
  return httpClient.get<Result<AiNodeConfigHistoryVO>>(`/admin/ai-node-history/detail/${historyId}`);
}

/** 回滚到指定历史版本 */
export function rollbackAiNodeHistory(id: string | number, historyId: string | number) {
  return httpClient.post<Result<void>>(`/admin/ai-node-history/${id}/rollback`, null, {
    params: { historyId },
  });
}