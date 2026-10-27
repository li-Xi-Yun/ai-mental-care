import httpClient from "@shared/api/instance";
import type { Result } from "@shared/api/types";

/**
 * 用户端 Live2D 模型配置 API
 * 对应后端 Live2dConfigController（/user/live2d-model）
 * 注意：modelId 为 Long，响应拦截器通过 BigInt 保护转成 string，前端透传不参与数值计算。
 */

/** 用户端 Live2D 模型配置 VO（对应后端 Live2dConfigVO） */
export interface Live2dConfigVO {
  /** 模型配置 id（长整型，走字符串） */
  modelId: number | string;
  /** 模型名称 */
  modelName: string;
  /** 入库相对路径，如 live2d-models/hiyori/hiyori.model3.json（需经 sign-url 换取签名 URL 后加载） */
  modelUrl: string;
  /** 模型封面图路径 */
  coverImagePath?: string | null;
  /** 表情固定词汇白名单 */
  expressionList: string[];
  /** 动作固定词汇白名单 */
  motionList: string[];
  /** 待机表情 id 列表（白名单子集；空数组/0 = 未启用待机配置） */
  idleExpressionList?: string[] | null;
  /** 待机动作 id 列表（白名单子集；空数组/0 = 未启用待机配置） */
  idleMotionList?: string[] | null;
  /** 待机操作间隔（秒）；0=不启用 */
  idleIntervalSeconds?: number | null;
  /** 是否启用 pixi-live2d-display 自带自动待机(模型 Idle 组)：1-是(库自动播)，0-否(按后台待机设置；未设置则静止) */
  idleAutoPlay?: number | null;
}

/**
 * 获取当前启用的 Live2D 模型配置；无启用模型时 data 为 null（前端判空隐藏形象组件）。
 * 需登录（JWT 拦截器自动附带 user token）。
 */
export async function getEnabledLive2dConfig(): Promise<Live2dConfigVO | null> {
  const res = await httpClient.get<Result<Live2dConfigVO | null>>("/user/live2d-model/config");
  return res?.data?.data ?? null;
}

/**
 * 签发模型文件签名下载 URL（需登录）。
 * 返回相对绝对路径（如 /user/live2d-model/file?path=...&expires=...&sign=...），
 * 直接交给 Live2DModel.from() 加载（model3 内部子资源已被后端改写为同形式签名 URL）。
 * 签名默认 300s 过期，过期后业务方重新调用并触发组件 reload。
 */
export async function getSignUrl(path: string): Promise<string> {
  const res = await httpClient.get<Result<string>>("/user/live2d-model/sign-url", { params: { path } });
  return String(res?.data?.data ?? "");
}

/**
 * 解析 STOMP 表情/动作指令载荷：{"expression": "<资产id>"} / {"motion": "<资产id>"}。
 * 解析失败或字段缺失返回 null（业务层忽略该条指令）。
 */
export function parseAvatarCommand(rawJson: string, kind: "expression" | "motion"): string | null {
  if (!rawJson) return null;
  try {
    const obj = JSON.parse(rawJson) as Record<string, unknown>;
    const id = obj?.[kind];
    return typeof id === "string" && id ? id : null;
  } catch {
    return null;
  }
}