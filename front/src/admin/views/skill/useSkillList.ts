import { computed, ref } from "vue";
import { DEFAULT_PAGE_SIZE, PAGINATION_LAYOUT } from "@/shared/api/config";

/** Skill 列表行（纯前端 mock） */
export interface SkillRow {
  id: number;
  name: string;
  type: string;
  description: string;
  status: string;
  createdTime: string;
}

/** Skill 状态徽章元数据（mock 状态为字符串：启用绿 / 禁用灰；供详情弹窗复用） */
export const SKILL_STATUS_BADGE_META: Record<string, { label: string; color: string; bg: string; border: string }> = {
  启用: { label: "启用", color: "#059669", bg: "#ecfdf5", border: "#a7f3d0" },
  禁用: { label: "禁用", color: "#6b7280", bg: "#f3f4f6", border: "#e5e7eb" },
};

/** 静态 mock 数据（本阶段不接后端） */
const SKILL_MOCK_LIST: SkillRow[] = [
  { id: 1, name: "情绪识别Skill", type: "情绪分析", description: "从对话文本中识别用户情绪类型及强度", status: "启用", createdTime: "2026-08-01 10:00" },
  { id: 2, name: "共情回复Skill", type: "对话增强", description: "生成具有共情能力的回复内容", status: "启用", createdTime: "2026-08-05 14:00" },
  { id: 3, name: "风险筛查Skill", type: "诊断辅助", description: "筛查对话中的自伤/自杀风险信号", status: "启用", createdTime: "2026-08-10 09:30" },
  { id: 4, name: "放松引导Skill", type: "对话增强", description: "引导用户进行呼吸放松和正念冥想", status: "禁用", createdTime: "2026-08-15 16:00" },
];

/**
 * Skill 管理页全部 UI 状态（单一职责）：
 * 列表 mock 数据 / 当前页码 / 新增弹窗显隐。
 * 本阶段保持纯 mock，不接后端接口。
 */
export function useSkillList() {
  const currentPage = ref(1);
  const showAddDialog = ref(false);
  const skillList = ref<SkillRow[]>(SKILL_MOCK_LIST);

  /* ==================== 详情弹窗状态（纯行数据，零请求） ==================== */

  const detailVisible = ref(false);
  const detailRow = ref<SkillRow | null>(null);

  /** 打开详情弹窗：仅用行数据渲染，不发起任何接口请求 */
  function openDetail(row: SkillRow) {
    detailRow.value = row;
    detailVisible.value = true;
  }

  /** 统计卡数据（基于 mock 列表计算） */
  const totalCount = computed(() => skillList.value.length);
  const enabledCount = computed(() => skillList.value.filter((item) => item.status === "启用").length);
  const disabledCount = computed(() => skillList.value.filter((item) => item.status === "禁用").length);

  return {
    currentPage,
    showAddDialog,
    skillList,
    totalCount,
    enabledCount,
    disabledCount,
    detailVisible,
    detailRow,
    openDetail,
    DEFAULT_PAGE_SIZE,
    PAGINATION_LAYOUT,
  };
}

export type UseSkillList = ReturnType<typeof useSkillList>;
