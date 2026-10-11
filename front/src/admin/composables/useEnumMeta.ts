import type { EnumMetaItem, TagType } from "@/admin/types";

/**
 * 枚举元数据工具：由状态/性别等枚举值 + 元数据记录生成统一的
 * label / 徽章样式 / 圆点样式 / tag 类型，消除各页面重复的
 * statusLabel / statusBadgeStyle / genderTagStyle 等手写函数。
 */
export function useEnumMeta<T extends Record<number, EnumMetaItem>>(
  meta: T,
  options: { fallbackKey?: keyof T; tagTypeMap?: Partial<Record<keyof T, TagType | undefined>> } = {},
) {
  const fallbackKey = options.fallbackKey ?? (0 as keyof T);

  function itemOf(key?: number | null): EnumMetaItem {
    const k = key as keyof T;
    return (meta[k] ?? meta[fallbackKey]) as EnumMetaItem;
  }

  function labelOf(key?: number | null): string {
    return itemOf(key).label;
  }

  /** 徽章内联样式：{ color, background, borderColor } */
  function styleOf(key?: number | null): Record<string, string> {
    const item = itemOf(key);
    return { color: item.color, background: item.bg, borderColor: item.border };
  }

  /** 圆点内联样式：{ background } */
  function dotStyleOf(key?: number | null): Record<string, string> {
    return { background: itemOf(key).color };
  }

  function tagTypeOf(key?: number | null): TagType {
    return options.tagTypeMap?.[key as keyof T] ?? "info";
  }

  return { itemOf, labelOf, styleOf, dotStyleOf, tagTypeOf };
}

export type UseEnumMeta = ReturnType<typeof useEnumMeta>;
