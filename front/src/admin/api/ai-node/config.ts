import httpClient from "@shared/api/instance";
import type { Result, PageResult } from "@shared/api/types";

/** AI节点配置详情 VO（对应后端 AiNodeConfigVO） */
export interface AiNodeConfigVO {
  id?: number;
  nodeKey?: string | null;
  nodeName?: string | null;
  nodeGroup?: string;
  systemPrompt?: string | null;
  modelType?: number | null;
  deepseekModelName?: string | null;
  ollamaModelName?: string | null;
  dashscopeModelName?: string | null;
  openaiModelName?: string | null;
  maxToken?: number | null;
  temperature?: number | null;
  topP?: number | null;
  topK?: number | null;
  frequencyPenalty?: number | null;
  presencePenalty?: number | null;
  responseFormat?: number | null;
  stopSequences?: string | null;
  retryMaxAttempts?: number | null;
  retryDelay?: number | null;
  retryMultiplier?: number | null;
  enabled?: number | null;
  sort?: number | null;
  remark?: string | null;
  version?: number | null;
  createdTime?: string | null;
  createdBy?: number | null;
  updatedTime?: string | null;
  updatedBy?: number | null;
}

/** AI节点配置修改入参（UpdateGroup 校验：nodeName / systemPrompt / modelType / maxToken / temperature / topP / version 必填） */
export interface AiNodeConfigUpdateDTO {
  nodeName: string;
  nodeKey?: string | null;
  nodeGroup?: string;
  systemPrompt: string;
  modelType: number;
  deepseekModelName?: string | null;
  ollamaModelName?: string | null;
  dashscopeModelName?: string | null;
  openaiModelName?: string | null;
  maxToken: number;
  temperature: number;
  topP: number;
  topK?: number | null;
  frequencyPenalty?: number | null;
  presencePenalty?: number | null;
  responseFormat?: number | null;
  stopSequences?: string | null;
  retryMaxAttempts: number;
  retryDelay: number;
  retryMultiplier: number;
  enabled?: number | null;
  sort?: number | null;
  remark?: string | null;
  version: number;
}

/** AI节点配置简要 VO（分页列表展示，不含提示词全文，对应后端 AiNodeConfigSimpleVO） */
export interface AiNodeConfigSimpleVO {
  id?: number | null;
  nodeKey?: string | null;
  nodeName?: string | null;
  nodeGroup?: string;
  modelType?: number | null;
  modelName?: string | null;
  enabled?: number | null;
  sort?: number | null;
  version?: number | null;
  updatedTime?: string | null;
  remark?: string | null;
}

/** AI节点分组 VO（对应后端 AiNodeGroupVO） */
export interface AiNodeGroupVO {
  nodeGroup?: string;
  count?: number | null;
}

/** AI节点配置分页查询入参（对应后端 AdminAiNodeConfigQueryDTO，keyword 同时映射到 nodeKey/nodeName 模糊搜索） */
export interface AdminAiNodeConfigQueryDTO {
  pageNum: number;
  pageSize: number;
  nodeKey?: string | null;
  nodeName?: string | null;
  nodeGroup?: string;
  modelType?: number | null;
  enabled?: number | null;
}

/** AI节点配置新增入参（AddGroup 校验：nodeKey/nodeName/systemPrompt/modelType/maxToken/temperature/topP/retry* 必填） */
export interface AiNodeConfigCreateDTO {
  nodeKey: string;
  nodeName: string;
  nodeGroup?: string;
  systemPrompt: string;
  modelType: number;
  deepseekModelName?: string | null;
  ollamaModelName?: string | null;
  dashscopeModelName?: string | null;
  openaiModelName?: string | null;
  maxToken: number;
  temperature: number;
  topP: number;
  topK?: number | null;
  frequencyPenalty?: number | null;
  presencePenalty?: number | null;
  responseFormat?: number | null;
  stopSequences?: string | null;
  retryMaxAttempts: number;
  retryDelay: number;
  retryMultiplier: number;
  enabled?: number | null;
  sort?: number | null;
  remark?: string | null;
}

/** AI节点配置批量启用/禁用入参（对应后端 AiNodeConfigBatchEnabledDTO，注意传 nodeKeys 而非 ids） */
export interface AiNodeConfigBatchEnabledDTO {
  nodeKeys: string[];
  enabled: number;
}

/** AI节点提示词详情 VO（对应后端 AiNodePromptVO） */
export interface AiNodePromptVO {
  nodeKey?: string | null;
  systemPrompt?: string | null;
  version?: number | null;
}

/** AI节点提示词修改入参 */
export interface AiNodePromptUpdateDTO {
  systemPrompt: string;
  version: number;
}

/** AI节点配置缓存状态 VO（对应后端 AiNodeCacheVO） */
export interface AiNodeCacheVO {
  cacheSize?: number | null;
  nodeKeys?: string[] | null;
}

/** 获取AI节点配置详情 */
export function getAiNodeConfigDetail(id: string | number) {
  return httpClient.get<Result<AiNodeConfigVO>>(`/admin/ai-node-config/${id}`);
}

/** 修改AI节点配置（需传 version 乐观锁校验） */
export function updateAiNodeConfig(id: string | number, data: AiNodeConfigUpdateDTO) {
  return httpClient.put<Result<void>>(`/admin/ai-node-config/${id}`, data);
}

/** 获取AI节点系统提示词 */
export function getAiNodePrompt(id: string | number) {
  return httpClient.get<Result<AiNodePromptVO>>(`/admin/ai-node-prompt/${id}`);
}

/** 单独修改AI节点系统提示词（需传 version 乐观锁，自动记录变更历史） */
export function updateAiNodePrompt(id: string | number, data: AiNodePromptUpdateDTO) {
  return httpClient.put<Result<void>>(`/admin/ai-node-prompt/${id}`, data);
}

/** 获取AI节点配置缓存状态 */
export function getAiNodeCacheStatus() {
  return httpClient.get<Result<AiNodeCacheVO>>("/admin/ai-node-cache/status");
}

/** 全量刷新AI节点配置缓存 */
export function refreshAiNodeCacheAll() {
  return httpClient.post<Result<void>>("/admin/ai-node-cache/refresh");
}

/** 刷新单个AI节点配置缓存 */
export function refreshAiNodeCache(id: string | number) {
  return httpClient.post<Result<void>>(`/admin/ai-node-cache/refresh/${id}`);
}

/** 分页查询AI节点配置（列表） */
export function pageAiNodeConfig(data: AdminAiNodeConfigQueryDTO) {
  return httpClient.post<Result<PageResult<AiNodeConfigSimpleVO>>>("/admin/ai-node-config/page", data);
}

/** 新增AI节点配置 */
export function createAiNodeConfig(data: AiNodeConfigCreateDTO) {
  return httpClient.post<Result<void>>("/admin/ai-node-config", data);
}

/** 删除AI节点配置 */
export function deleteAiNodeConfig(id: string | number) {
  return httpClient.delete<Result<void>>(`/admin/ai-node-config/${id}`);
}

/** 批量启用/禁用AI节点配置 */
export function batchUpdateAiNodeConfigEnabled(data: AiNodeConfigBatchEnabledDTO) {
  return httpClient.put<Result<void>>("/admin/ai-node-config/batch-enabled", data);
}

/** 获取节点分组列表（去重分组及数量） */
export function getAiNodeGroups() {
  return httpClient.get<Result<AiNodeGroupVO[]>>("/admin/ai-node-config/groups");
}