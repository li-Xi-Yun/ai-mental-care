import dayjs from "@/shared/utils/dayjs";

/**
 * 时间格式化工具（统一 "YYYY-MM-DD HH:mm" 等格式），
 * 从各页面重复的 formatTime 中抽离。
 */
export function useTimeFormat() {
  function formatTime(v?: string | number | null, pattern = "YYYY-MM-DD HH:mm"): string {
    if (v === undefined || v === null || v === "") return "—";
    const d = dayjs(v);
    return d.isValid() ? d.format(pattern) : "—";
  }

  return { formatTime };
}

export type UseTimeFormat = ReturnType<typeof useTimeFormat>;

/** 独立可用的纯函数版本 */
export function formatTime(v?: string | number | null, pattern = "YYYY-MM-DD HH:mm"): string {
  if (v === undefined || v === null || v === "") return "—";
  const d = dayjs(v);
  return d.isValid() ? d.format(pattern) : "—";
}