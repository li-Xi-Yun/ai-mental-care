import { computed, ref } from "vue";
import { useRouter } from "vue-router";
import { useUserStore } from "@/portal/stores/user";
import { ElMessageBox } from "element-plus";
import { getScaleList, type ScaleCategoryVO, type ScaleVO } from "@/portal/api/scale/scale";
import { getScaleCategoryList } from "@/portal/api/scale/category";
import { getUnfinishedAssessment, terminateAssessment } from "@/portal/api/scale/user-scale";
import { dayjs, FORMAT_DATE } from "@/shared/utils";
import { SCALE_DEFAULT_PAGE_SIZE } from "@/shared/api/config";

export function useScaleList() {
  const router = useRouter();
  const userStore = useUserStore();

  const activeCategory = ref<number | "all">("all");
  const keyword = ref("");
  const scalesLoading = ref(false);
  const pageNum = ref(1);
  const pageSize = ref(SCALE_DEFAULT_PAGE_SIZE);
  const total = ref(0);
  const categoryCount = ref(0);
  const scaleList = ref<ScaleVO[]>([]);
  const categories = ref<ScaleCategoryVO[]>([]);

  const categoryTabs = computed(() => [
    { key: "all" as const, label: "全部", scaleCount: total.value },
    ...categories.value.map((c) => ({
      key: c.id as number,
      label: c.categoryName,
      scaleCount: c.scaleCount,
    })),
  ]);

  const totalScaleCount = computed(() =>
    categories.value.reduce((sum, c) => sum + Number(c.scaleCount || 0), 0),
  );

  async function loadCategories() {
    try {
      const res = await getScaleCategoryList();
      const list = res.data?.data ?? [];
      categories.value = list;
      categoryCount.value = list.length;
    } catch {
      categories.value = [];
      categoryCount.value = 0;
    }
  }

  async function fetchScales() {
    scalesLoading.value = true;
    try {
      const res = await getScaleList({
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        ...(activeCategory.value !== "all" ? { scaleCategoryId: activeCategory.value } : {}),
        ...(keyword.value.trim() ? { keyword: keyword.value.trim() } : {}),
      });
      const page = res.data?.data;
      scaleList.value = page?.records ?? [];
      total.value = page?.total ?? 0;
    } catch {
      scaleList.value = [];
      total.value = 0;
    } finally {
      scalesLoading.value = false;
    }
  }

  async function handleFilterChange() {
    pageNum.value = 1;
    await fetchScales();
  }

  async function updateCategory(key: number | "all") {
    if (activeCategory.value === key) return;
    activeCategory.value = key;
    await handleFilterChange();
  }

  function handleSearch() {
    void handleFilterChange();
  }

  async function guardAndStart(scale: ScaleVO) {
    if (!userStore.token) {
      userStore.openLoginDialog("login");
      return;
    }
    let unfinished: { hasUnfinished: boolean; recordId?: number; startTime?: string } | undefined;
    try {
      const res = await getUnfinishedAssessment(scale.id);
      unfinished = res.data?.data ?? undefined;
    } catch {
      // 查询接口失败：不阻塞
    }

    if (!unfinished?.hasUnfinished || unfinished.recordId == null) {
      router.push(`/scale/${scale.id}/answer`);
      return;
    }

    const startText = unfinished.startTime
      ? dayjs(unfinished.startTime).isValid()
        ? dayjs(unfinished.startTime).format(FORMAT_DATE)
        : String(unfinished.startTime)
      : "—";

    try {
      await ElMessageBox.confirm(
        `检测到您有未完成的测评（上次开始于 ${startText}），是否继续作答？`,
        "继续测评",
        {
          confirmButtonText: "继续作答",
          cancelButtonText: "重新开始",
          closeOnClickModal: false,
          distinguishCancelAndClose: true,
          type: "warning",
        },
      );
      router.push(`/scale/${scale.id}/answer?recordId=${unfinished.recordId}`);
    } catch (reason) {
      if (reason === "cancel") {
        try {
          await terminateAssessment(unfinished.recordId);
        } catch {
          // 终止失败不阻塞
        }
        router.push(`/scale/${scale.id}/answer`);
      } else if (reason === "close") {
        return;
      } else {
        throw reason;
      }
    }
  }

  return {
    activeCategory,
    keyword,
    scalesLoading,
    pageNum,
    pageSize,
    total,
    categoryCount,
    scaleList,
    categories,
    categoryTabs,
    totalScaleCount,
    loadCategories,
    fetchScales,
    handleFilterChange,
    updateCategory,
    handleSearch,
    guardAndStart,
  };
}