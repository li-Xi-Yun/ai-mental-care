/**
 * 时间与时区工具
 *
 * 后端所有 LocalDateTime / Date 均以 **UTC** 存储并通过 Jackson 以 `yyyy-MM-dd HH:mm:ss`
 * 字符串返回（字符串本身不携带时区标记，代表的是 UTC 时间）。
 * 前端默认展示与录入都是**本地时区**（浏览器 Intl 决定的系统时区），因此：
 *  - 收到后端 `"2026-10-07 12:00:00"`（UTC）时，需要 `utcToLocal` 转换为本地展示时间；
 *  - 提交 `"2026-10-07 12:00:00"`（本地）时，需要 `localToUtc` 转成 UTC 字符串再发给后端。
 *
 * 本工具利用 dayjs 完成解析/格式化，并通过 `Intl.DateTimeFormat().resolvedOptions().timeZone`
 * 获取本地时区名（或退化用分钟偏移），不引入额外依赖。
 */

import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import customParseFormat from "dayjs/plugin/customParseFormat";

dayjs.extend(utc);
dayjs.extend(customParseFormat);

/** 后端 / 前端输入输出的统一时间格式 */
export const FORMAT_UTC = "YYYY-MM-DD HH:mm:ss";
/** 日期 */
export const FORMAT_DATE_ONLY = "YYYY-MM-DD";

/** 获取本地时区名（如 Asia/Shanghai）；取不到时返回偏移分钟字符串 */
export function getLocalTimeZone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || `UTC+${-dayjs().utcOffset() / 60}`;
  } catch {
    return `UTC${dayjs().utcOffset() ? (dayjs().utcOffset() > 0 ? "+" : "") + dayjs().utcOffset() / 60 : ""}`;
  }
}

/** 本地时区偏移分钟数（东八区为 480） */
export function getLocalOffsetMinutes(): number {
  return dayjs().utcOffset();
}

/** 当前本地时区的时区偏移标记，用于时间字符串后缀（如 +08:00） */
export function getLocalOffsetTag(): string {
  const offset = dayjs().utcOffset();
  const sign = offset >= 0 ? "+" : "-";
  const abs = Math.abs(offset);
  const hh = String(Math.floor(abs / 60)).padStart(2, "0");
  const mm = String(abs % 60).padStart(2, "0");
  return `${sign}${hh}:${mm}`;
}

/** 判断后端时间字符串是否为表示日期时间（形如 yyyy-MM-dd ...） */
function isDateTimeString(value: unknown): value is string {
  return typeof value === "string" && /^\d{4}-\d{2}-\d{2}[ T]/.test(value);
}

/**
 * 判断一个字符串是否为「UTC 时间字符串」
 * 规则：解析后若能视为合法日期时间、且时区偏移为 0（日+0/+0:00/含 Z 结尾）即视为 UTC。
 */
export function isUtcString(value: unknown): boolean {
  if (!isDateTimeString(value)) return false;
  const normalized = value.trim();
  if (/([zZ]|[+-]\d{2}:?\d{2})$/.test(normalized)) {
    return /z$/i.test(normalized) || /[+-]00:?00$/.test(normalized);
  }
  // 无时区标记 → 后端约定为 UTC
  return true;
}

/**
 * 把后端返回的 UTC 时间字符串转换为本地时区时间字符串（后端 `yyyy-MM-dd HH:mm:ss` → 本地）。
 * 输入若不是可解析的时间字符串则原样返回。
 */
export function utcToLocal(value: unknown): unknown {
  if (!isDateTimeString(value)) return value;
  const t = (value as string).trim();
  // 若字符串已经带时区标记，交给 dayjs.utc 解析
  if (/([zZ]|[+-]\d{2}:?\d{2})$/.test(t)) {
    const d = dayjs(t);
    if (!d.isValid()) return value;
    return d.local().format(FORMAT_UTC);
  }
  // 无标记按 UTC 解析
  const d = dayjs.utc(t, FORMAT_UTC);
  if (!d.isValid()) return value;
  return d.local().format(FORMAT_UTC);
}

/**
 * 把本地时间字符串（无时区）转换为 UTC 时间字符串（`yyyy-MM-dd HH:mm:ss`）。
 * 若输入已经带时区标记则按该标记解析并转成 UTC 表示。
 */
export function localToUtc(value: unknown): unknown {
  if (!isDateTimeString(value)) return value;
  const t = (value as string).trim();
  let d: dayjs.Dayjs;
  if (/([zZ]|[+-]\d{2}:?\d{2})$/.test(t)) {
    d = dayjs(t);
    if (!d.isValid()) return value;
  } else {
    d = dayjs(t, FORMAT_UTC);
    if (!d.isValid()) return value;
  }
  return d.utc().format(FORMAT_UTC);
}

/**
 * 递归处理对象/数组中的时间字符串。
 * @param mode 'toLocal' 响应阶段：UTC → 本地；'toUtc' 请求阶段：本地 → UTC
 */
export function processTimeFields(target: unknown, mode: "toLocal" | "toUtc" = "toLocal"): unknown {
  if (target === null || target === undefined) return target;
  if (Array.isArray(target)) {
    return target.map((item) => processTimeFields(item, mode));
  }
  if (typeof target !== "object") {
    return mode === "toUtc" ? localToUtc(target) : utcToLocal(target);
  }

  const result: Record<string, unknown> = {};
  for (const key of Object.keys(target as Record<string, unknown>)) {
    result[key] = processTimeFields((target as Record<string, unknown>)[key], mode);
  }
  return result;
}

/** 时间字符串 → Date（本地语义） */
export function toLocalDate(value: string): Date | null {
  const d = dayjs(value);
  return d.isValid() ? d.toDate() : null;
}

/** Date → 本地时间字符串 */
export function formatLocal(value: Date | string): string {
  const d = typeof value === "string" ? dayjs(value) : dayjs(value);
  return d.isValid() ? d.format(FORMAT_UTC) : "";
}

/** Date → UTC 时间字符串 */
export function formatUtc(value: Date | string): string {
  const d = typeof value === "string" ? dayjs(value) : dayjs(value);
  return d.isValid() ? d.utc().format(FORMAT_UTC) : "";
}

/** 毫秒时间戳 → 本地时间字符串 */
export function timestampToLocal(ms: number): string {
  return dayjs(ms).format(FORMAT_UTC);
}