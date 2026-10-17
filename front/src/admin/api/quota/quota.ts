import httpClient from "@shared/api/instance";
import type { Result } from "@shared/api/types";

/**
 * AI 对话配额-全局默认配置 VO
 * 与后端 QuotaConfigVO 字段对齐。
 */
export interface QuotaConfigVO {
  /** 资源类型：1=TEXT 2=ASR 3=TTS 4=AVATAR_2D */
  resourceType: number;
  /** 资源名称 */
  resourceName?: string;
  /** 全局默认次数上限（null 表示未配置） */
  defaultLimit?: number | null;
  /** 资源可用性：1=可用，0=已停用 */
  available?: number;
  /** 是否启用限流：1=已启用，0=未启用 */
  enabled?: number;
  /** 计量周期：0=终身累计，1=按月重置 */
  cycleType?: number;
  /** 备注 */
  remark?: string;
}

/**
 * AI 对话配额-全局默认配置更新 DTO
 * 与后端 QuotaConfigUpdateDTO 对齐：全字段可空（局部更新），仅更新传入字段。
 */
export interface QuotaConfigUpdateDTO {
  /** 全局默认次数上限（null 表示不修改） */
  defaultLimit?: number | null;
  /** 资源可用性：1=可用，0=已停用 */
  available?: number;
  /** 是否启用限流：1=已启用，0=未启用 */
  enabled?: number;
  /** 计量周期：0=终身累计，1=按月重置 */
  cycleType?: number;
  /** 备注 */
  remark?: string;
}

/**
 * AI 对话配额-单用户配额 VO
 * 与后端 QuotaUserVO 字段对齐。
 */
export interface QuotaUserVO {
  /** 资源类型：1=TEXT 2=ASR 3=TTS 4=AVATAR_2D */
  resourceType: number;
  /** 资源名称 */
  resourceName?: string;
  /** 个性化上限，null=跟随全局默认 */
  customLimit?: number | null;
  /** 不限量（豁免）：1=不限额、不计数，0=正常限流 */
  unlimited?: number;
  /** 资源可用性：1=可用，0=已停用 */
  available?: number;
  /** 已用次数 */
  usedCount?: number;
  /** 生效上限（unlimited=1 / available=0 / enabled=0 时为 null） */
  effectiveLimit?: number | null;
  /** 剩余次数（unlimited=1 或不可用时为 null） */
  remaining?: number | null;
}

/**
 * AI 对话配额-单用户配额调整 DTO
 * 与后端 QuotaUserAdjustDTO 对齐：customLimit 传 null 表示清除个性化；
 * unlimited 传 null 表示不修改豁免状态；used 必传。
 */
export interface QuotaUserAdjustDTO {
  /** 资源类型：1=TEXT 2=ASR 3=TTS 4=AVATAR_2D */
  resourceType: number;
  /** 个性化上限，null=跟随全局默认 */
  customLimit?: number | null;
  /** 已用次数 */
  used: number;
  /** 不限量（豁免）：1=不限额，0=正常限流；null=不修改 */
  unlimited?: number | null;
}

/**
 * 查询全部资源类型全局配置（GET /admin/quota/config）
 * @returns 按 resourceType 排序的全局配置列表
 */
export function getQuotaConfigList() {
  return httpClient.get<Result<QuotaConfigVO[]>>("/admin/quota/config");
}

/**
 * 修改单个资源全局配置（PUT /admin/quota/config/{resourceType}）
 * 局部更新：仅更新传入字段，未传字段保持原值。
 * @param resourceType 资源类型 1~4
 * @param data 全局配置更新载荷
 */
export function updateQuotaConfig(resourceType: number, data: QuotaConfigUpdateDTO) {
  return httpClient.put<Result<void>>(`/admin/quota/config/${resourceType}`, data);
}

/**
 * 查询指定用户全部资源配额（GET /admin/quota/users/{userId}）
 * @param userId 用户ID
 * @returns 四资源配额列表（按 resourceType 排序）
 */
export function getUserQuotaList(userId: number | string) {
  return httpClient.get<Result<QuotaUserVO[]>>(`/admin/quota/users/${userId}`);
}

/**
 * 调整用户某资源配额（PUT /admin/quota/users/{userId}/resources/{resourceType}）
 * @param userId 用户ID
 * @param resourceType 资源类型 1~4
 * @param data 用户配额调整载荷
 */
export function adjustUserQuota(userId: number | string, resourceType: number, data: QuotaUserAdjustDTO) {
  return httpClient.put<Result<void>>(`/admin/quota/users/${userId}/resources/${resourceType}`, data);
}
