import { ref } from "vue";
import { useRouter } from "vue-router";
import { useUserStore } from "@/portal/stores/user";
import { ElMessage } from "element-plus";
import { getScaleDetail, type ScaleDetailVO } from "@/portal/api/scale/scale";
import {
  getUnfinishedAssessment,
  startAssessment,
  terminateAssessment,
  getScaleRecords,
} from "@/portal/api/scale/user-scale";
import { dayjs, FORMAT_DATE } from "@/shared/utils";

/** 最近一次测评结果 */
export interface LatestResult {
  recordId: number;
  totalScore?: number;
  createdTime?: string;
  resultText?: string;
  finishStatus?: number;
}

export function useScaleDetail(scaleId: number) {
  const router = useRouter();
  const userStore = useUserStore();

  const detail = ref<ScaleDetailVO | null>(null);
  const loading = ref(false);
  const loadError = ref(false);
  const starting = ref(false);
  const startErrorReason = ref("");
  const latestResult = ref<LatestResult | null>(null);
  const resultLoading = ref(false);

  async function fetchDetail() {
    loading.value = true;
    loadError.value = false;
    try {
      const res = await getScaleDetail(scaleId);
      detail.value = res.data?.data ?? null;
    } catch {
      detail.value = null;
      loadError.value = true;
    } finally {
      loading.value = false;
    }
  }

  async function fetchLatestResult() {
    if (!userStore.token) return;
    resultLoading.value = true;
    try {
      const res = await getScaleRecords({ pageNum: 1, pageSize: 1, scaleId, finishStatus: 1 });
      const records = res.data?.data?.records ?? [];
      if (records.length > 0) {
        const r = records[0];
        latestResult.value = {
          recordId: r.recordId,
          totalScore: r.totalScore,
          createdTime: r.createdTime,
          resultText: r.resultText,
          finishStatus: r.finishStatus,
        };
      } else {
        latestResult.value = null;
      }
    } catch {
      latestResult.value = null;
    } finally {
      resultLoading.value = false;
    }
  }

  async function handleStart() {
    if (!userStore.token) {
      userStore.openLoginDialog("login");
      return;
    }

    starting.value = true;
    startErrorReason.value = "";

    try {
      let unfinishedId: number | undefined;
      try {
        const unfinishedRes = await getUnfinishedAssessment(scaleId);
        const uf = unfinishedRes.data?.data;
        if (uf?.hasUnfinished && uf.recordId != null) {
          unfinishedId = uf.recordId;
        }
      } catch {
        // 查询失败不阻塞
      }

      if (unfinishedId != null) {
        try {
          await terminateAssessment(unfinishedId);
        } catch {
          // 终止不阻塞
        }
      }

      const startRes = await startAssessment({ scaleId });
      const recordId = startRes.data?.data?.recordId;
      if (recordId != null) {
        router.push(`/scale/${scaleId}/answer?recordId=${recordId}`);
      } else {
        router.push(`/scale/${scaleId}/answer`);
      }
    } catch {
      startErrorReason.value = "启动测评失败，请稍后重试。";
      ElMessage.error("启动测评失败");
    } finally {
      starting.value = false;
    }
  }

  function formatDate(v?: string): string {
    if (!v) return "--";
    const d = dayjs(v);
    return d.isValid() ? d.format(FORMAT_DATE) : String(v);
  }

  return {
    detail,
    loading,
    loadError,
    starting,
    startErrorReason,
    latestResult,
    resultLoading,
    fetchDetail,
    fetchLatestResult,
    handleStart,
    formatDate,
  };
}