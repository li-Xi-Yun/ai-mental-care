import httpClient from "@shared/api/instance";
import type { Result, PageResult } from "@shared/api/types";

/**
 * Live2D 模型配置管理端 API
 * 对应后端 AdminLive2dConfigController（/admin/live2d-model）
 * 注意：后端 Long 一律序列化为字符串（BigNumberSerializer），id 前端透传不参与数值计算。
 */

/** Live2D 模型配置管理端 VO（对应后端 Live2dConfigAdminVO） */
export interface Live2dConfigAdminVO {
  /** 自增主键（长整型，走字符串） */
  id?: number | string | null;
  /** Live2D 模型名称 */
  modelName?: string | null;
  /** 模型别名（中文展示名称） */
  aliasName?: string | null;
  /** .model3.json 服务器相对路径（前端加载入口） */
  modelUrl?: string | null;
  /** 模型包根目录相对路径 */
  modelRelativePath?: string | null;
  /** 模型封面图路径 */
  coverImagePath?: string | null;
  /** 是否启用：0-禁用 1-启用（全局唯一启用的互斥） */
  enabled?: number | null;
  /** 排序值，越大越靠前 */
  sort?: number | null;
  /** 语义解析说明（发给大模型的提示词） */
  semanticPrompt?: string | null;
  /** 动作固定词汇 Map：key=动作id（白名单固定，不可增删），value=中文含义（后台可编辑） */
  motionList?: Record<string, string> | null;
  /** 表情固定词汇 Map：key=表情id（白名单固定，不可增删），value=中文含义（后台可编辑） */
  expressionList?: Record<string, string> | null;
  /** 待机表情 id 列表（白名单子集；空数组/0 表示未启用待机配置） */
  idleExpressionList?: string[] | null;
  /** 待机动作 id 列表（白名单子集；空数组/0 表示未启用待机配置） */
  idleMotionList?: string[] | null;
  /** 待机操作间隔（秒）；0=不启用 */
  idleIntervalSeconds?: number | null;
  /** 是否启用 pixi-live2d-display 自带自动待机(模型 Idle 组)：1-是(库自动播)，0-否(按后台待机设置；未设置则静止) */
  idleAutoPlay?: number | null;
  /** 备注 */
  remark?: string | null;
  /** 创建时间 */
  createdTime?: string | null;
  /** 更新时间 */
  updatedTime?: string | null;
}

/** Live2D 模型配置修改入参（对应后端 Live2dConfigDTO，UpdateGroup：id 必填，其余字段空值不覆盖旧值） */
export interface Live2dConfigUpdateDTO {
  id: number | string;
  modelName?: string | null;
  aliasName?: string | null;
  modelUrl?: string | null;
  modelRelativePath?: string | null;
  coverImagePath?: string | null;
  semanticPrompt?: string | null;
  /** 动作 id → 中文含义（仅 key 在白名单内的 value 生效，新增 key 被忽略） */
  motionList?: Record<string, string> | null;
  /** 表情 id → 中文含义（仅 key 在白名单内的 value 生效，新增 key 被忽略） */
  expressionList?: Record<string, string> | null;
  /** 待机表情 id 列表（仅白名单子集生效，越权 id 被忽略；[] 清空） */
  idleExpressionList?: string[] | null;
  /** 待机动作 id 列表（仅白名单子集生效，越权 id 被忽略；[] 清空） */
  idleMotionList?: string[] | null;
  /** 待机操作间隔（秒）；0=不启用 */
  idleIntervalSeconds?: number | null;
  /** 是否启用 pixi-live2d-display 自带自动待机(模型 Idle 组)：1-是(库自动播)，0-否(按后台待机设置；未设置则静止) */
  idleAutoPlay?: number | null;
  enabled?: number | null;
  sort?: number | null;
  remark?: string | null;
}

/** Live2D 模型配置分页查询入参（对应后端 Live2dConfigQueryDTO；关键词命中模型名称或中文别名任一即返回） */
export interface Live2dConfigQuery {
  pageNum: number;
  pageSize: number;
  /** 模型名称 / 中文别名（模糊匹配，二者任一命中） */
  modelName?: string | null;
  /** 是否启用：0-禁用 1-启用 */
  enabled?: number | null;
}

/**
 * 上传 Live2D 模型包（zip → 解压 → 解析白名单 → 落库）
 * @param data multipart FormData：file(必) modelName(必) + 可选 aliasName/expressionFileName/motionFileName/semanticPrompt/sort/remark
 * @returns 新记录 id（字符串）
 */
export function uploadLive2dModel(data: FormData) {
  return httpClient.post<Result<string>>("/admin/live2d-model", data, {
    headers: { "Content-Type": "multipart/form-data" },
  });
}

/** 修改 Live2D 模型配置（更新语义提示词、封面等；DTO 空字段不覆盖旧值） */
export function updateLive2dModel(id: number | string, data: Live2dConfigUpdateDTO) {
  return httpClient.put<Result<void>>(`/admin/live2d-model/${id}`, data);
}

/** 获取 Live2D 模型配置详情（含表情/动作白名单、语义提示词） */
export function getLive2dModelDetail(id: number | string) {
  return httpClient.get<Result<Live2dConfigAdminVO>>(`/admin/live2d-model/${id}`);
}

/** 删除 Live2D 模型配置（逻辑删除） */
export function deleteLive2dModel(id: number | string) {
  return httpClient.delete<Result<void>>(`/admin/live2d-model/${id}`);
}

/** 启用/切换 Live2D 模型（enabled=1 时全局唯一启用互斥） */
export function enableLive2dModel(id: number | string, enabled: number) {
  return httpClient.put<Result<void>>(`/admin/live2d-model/${id}/enable`, null, { params: { enabled } });
}

/** 分页查询 Live2D 模型配置 */
export function pageLive2dModels(data: Live2dConfigQuery) {
  return httpClient.post<Result<PageResult<Live2dConfigAdminVO>>>("/admin/live2d-model/page", data);
}