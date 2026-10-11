import { ref, type Ref } from "vue";
import { DEFAULT_PAGE_SIZE } from "@/shared/api/config";

export interface UsePaginationOptions {
  /** 每页条数 */
  pageSize?: number;
  /** 变化时触发的加载函数（翻页、改页大小） */
  onPageChange?: () => void | Promise<void>;
}

/**
 * 分页状态封装：currentPage / pageSize / total / changePage。
 * 统一各列表页的分页逻辑（翻页重置当前页码并触发加载）。
 */
export function usePagination(options: UsePaginationOptions = {}) {
  const currentPage = ref(1);
  const pageSize = ref(options.pageSize ?? DEFAULT_PAGE_SIZE);
  const total = ref(0);

  function setTotal(value: number) {
    total.value = value;
  }

  async function changePage(page: number) {
    if (page < 1) return;
    currentPage.value = page;
    await options.onPageChange?.();
  }

  /** 搜索/重置时回到第一页 */
  async function resetPage() {
    currentPage.value = 1;
    await options.onPageChange?.();
  }

  return { currentPage, pageSize, total, setTotal, changePage, resetPage };
}

export type UsePagination = ReturnType<typeof usePagination>;

/** 供模板直接取 ref 解包的类型别名 */
export type { Ref };