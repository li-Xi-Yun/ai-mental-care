import { computed, ref, watch } from "vue";
import { useRoute } from "vue-router";
import {
  ADMIN_NAV_ITEMS,
  ADMIN_PLATFORM_TITLE,
  isAdminNavGroup,
  type AdminNavGroup,
  type AdminNavLeaf,
} from "@/shared/api/config";
import { getStorage, setStorage } from "@/shared/utils/storage";

/** 用户手动折叠的分组 key 列表（稀疏存储；未记录的分组默认展开） */
const NAV_COLLAPSED_GROUPS_KEY = "admin-nav-collapsed-groups";

/** 规范化路径：去 query/hash 与尾部斜杠 */
export function normalizeAdminPath(path: string): string {
  const base = (path || "/").split("?")[0].split("#")[0];
  return base.length > 1 ? base.replace(/\/+$/, "") : base;
}

/** 路由别名：/admin 首页复用用户管理页 */
const ADMIN_PATH_ALIASES: Record<string, string> = { "/admin": "/admin/user" };

/** 解析导航目标路径（含别名映射） */
export function resolveAdminNavTarget(path: string): string {
  const p = normalizeAdminPath(path);
  return ADMIN_PATH_ALIASES[p] ?? p;
}

export interface AdminNavMatch {
  leaf: AdminNavLeaf;
  group: AdminNavGroup | null;
  /** true = 列表页本体；false = 详情等更深层路由 */
  exact: boolean;
}

/**
 * 在导航配置里为当前路径寻找匹配叶子（最长前缀优先）：
 * - /admin/scale/records → 测评记录（而非 量表管理）
 * - /admin/scale/:id → 量表管理（exact=false）
 * - /admin/ai-node/:id → AI节点配置；/admin/file/vector/:id → 文件管理
 * - /admin → 用户管理（别名）
 */
export function findAdminNavMatch(path: string): AdminNavMatch | null {
  const target = resolveAdminNavTarget(path);
  let best: AdminNavMatch | null = null;
  for (const entry of ADMIN_NAV_ITEMS) {
    const group = isAdminNavGroup(entry) ? entry : null;
    const leaves = group ? group.children : [entry as AdminNavLeaf];
    for (const leaf of leaves) {
      const route = normalizeAdminPath(leaf.route);
      // 命中规则：完全相等，或 target 是 route 的下一级子路径（避免 /admin/users 误命中 /admin/user）
      if (target !== route && !target.startsWith(route + "/")) continue;
      if (!best || route.length > normalizeAdminPath(best.leaf.route).length) {
        best = { leaf, group, exact: target === route };
      }
    }
  }
  return best;
}

/** 顶栏面包屑项 */
export interface AdminBreadcrumbItem {
  key: string;
  label: string;
  /** 有 route 且非 current → 渲染为链接 */
  route?: string;
  /** 恰好最后一项为 true → 渲染为 .header-title（E2E 选择器保证存在） */
  current?: boolean;
}

/**
 * 推导顶栏面包屑：后台 › 分组 › 当前页；
 * 详情路由追加父列表页链接（顶替原详情页私有面包屑的返回能力），末级用 route.meta.title。
 */
export function buildAdminBreadcrumb(path: string, metaTitle?: string): AdminBreadcrumbItem[] {
  const match = findAdminNavMatch(path);
  const crumbs: AdminBreadcrumbItem[] = [{ key: "root", label: "后台", route: "/admin/user" }];
  if (match) {
    if (match.group) crumbs.push({ key: match.group.key, label: match.group.label });
    if (match.exact) {
      crumbs.push({ key: match.leaf.key, label: match.leaf.label, current: true });
    } else {
      crumbs.push({ key: match.leaf.key, label: match.leaf.label, route: match.leaf.route });
      crumbs.push({ key: "current", label: metaTitle || match.leaf.label, current: true });
    }
  } else {
    crumbs.push({ key: "current", label: metaTitle || ADMIN_PLATFORM_TITLE, current: true });
  }
  return crumbs;
}

function readCollapsedGroups(): string[] {
  const saved = getStorage<unknown>(NAV_COLLAPSED_GROUPS_KEY, []);
  return Array.isArray(saved) ? saved.filter((k): k is string => typeof k === "string") : [];
}

/**
 * 侧边栏分组导航 + 顶栏面包屑的共享逻辑：
 * - groups/leaves：拆分后的导航条目
 * - activeLeafKey/activeGroupKey：当前路由命中的叶子与分组
 * - 分组折叠状态（localStorage 持久化，只记录用户手动折叠的 key，默认全展开）
 * - 路由变化进入某分组时自动展开（用户在本页手动折叠不会被强制弹开）
 */
export function useAdminNav() {
  const route = useRoute();

  const groups = computed(() => ADMIN_NAV_ITEMS.filter(isAdminNavGroup));
  const leaves = computed(() => ADMIN_NAV_ITEMS.filter((entry): entry is AdminNavLeaf => !isAdminNavGroup(entry)));

  const activeMatch = computed(() => findAdminNavMatch(route.path));
  const activeLeafKey = computed(() => activeMatch.value?.leaf.key ?? "");
  const activeGroupKey = computed(() => activeMatch.value?.group?.key ?? "");

  const breadcrumbs = computed<AdminBreadcrumbItem[]>(() =>
    buildAdminBreadcrumb(route.path, typeof route.meta.title === "string" ? route.meta.title : ""),
  );

  const collapsedGroups = ref<string[]>(readCollapsedGroups());
  const isGroupExpanded = (key: string) => !collapsedGroups.value.includes(key);

  function persist() {
    setStorage(NAV_COLLAPSED_GROUPS_KEY, collapsedGroups.value);
  }

  function toggleGroup(key: string) {
    collapsedGroups.value = isGroupExpanded(key)
      ? [...collapsedGroups.value, key]
      : collapsedGroups.value.filter((k) => k !== key);
    persist();
  }

  function expandGroup(key: string) {
    if (isGroupExpanded(key)) return;
    collapsedGroups.value = collapsedGroups.value.filter((k) => k !== key);
    persist();
  }

  // 仅在路由变化进入某分组时自动展开；immediate 保证刷新/直达详情 URL 时激活分组可见
  watch(
    activeGroupKey,
    (key, prev) => {
      if (key && key !== prev) expandGroup(key);
    },
    { immediate: true },
  );

  return {
    groups,
    leaves,
    activeLeafKey,
    activeGroupKey,
    isGroupExpanded,
    toggleGroup,
    expandGroup,
    breadcrumbs,
  };
}

export type UseAdminNav = ReturnType<typeof useAdminNav>;
