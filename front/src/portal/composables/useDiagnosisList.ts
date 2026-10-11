import { reactive, ref } from "vue";
import { getEmotionDiagnosisList, getEmotionDiagnosisListByConversation } from "@/portal/api/conversation/emotion-diagnosis";
import type { DiagnosisConversationVO, EmotionDiagnosisListVO } from "@/portal/api/conversation/emotion-diagnosis";
import { DEFAULT_PAGE_SIZE } from "@/shared/api/config";

/** 会话必须有非空 conversationId 才能参与展示（上游收敛，避免调用方处理 number|undefined） */
type SessionVO = DiagnosisConversationVO & { conversationId: number };

export function useDiagnosisList() {
  const keyword = ref("");
  const pageNum = ref(1);
  const total = ref(0);
  const sessions = ref<SessionVO[]>([]);
  const loading = ref(false);
  const loadError = ref(false);

  const expandedConversationId = ref<number | null>(null);
  const recordMap = reactive<Record<number, EmotionDiagnosisListVO[]>>({});
  const recordsLoadingMap = reactive<Record<number, boolean>>({});

  function resetExpandState() {
    expandedConversationId.value = null;
    for (const key of Object.keys(recordMap)) {
      delete recordMap[Number(key)];
    }
    for (const key of Object.keys(recordsLoadingMap)) {
      delete recordsLoadingMap[Number(key)];
    }
  }

  async function fetchList() {
    loading.value = true;
    loadError.value = false;
    resetExpandState();
    try {
      const keywordTrimmed = keyword.value.trim();
      const res = await getEmotionDiagnosisList({
        pageNum: pageNum.value,
        pageSize: DEFAULT_PAGE_SIZE,
        ...(keywordTrimmed ? { conversationName: keywordTrimmed } : {}),
      });
      const page = res.data?.data;
      sessions.value = (page?.records ?? []).filter((s): s is SessionVO => s.conversationId != null);
      total.value = page?.total ?? 0;
    } catch {
      sessions.value = [];
      total.value = 0;
      loadError.value = true;
    } finally {
      loading.value = false;
    }
  }

  async function fetchRecordsForConversation(conversationId: number) {
    if (recordsLoadingMap[conversationId] || conversationId in recordMap) return;
    recordsLoadingMap[conversationId] = true;
    try {
      const res = await getEmotionDiagnosisListByConversation(String(conversationId), {});
      recordMap[conversationId] = res.data?.data?.records ?? [];
    } catch {
      recordMap[conversationId] = [];
    } finally {
      recordsLoadingMap[conversationId] = false;
    }
  }

  function toggleExpand(session: SessionVO) {
    const id = session.conversationId;
    if (expandedConversationId.value === id) {
      expandedConversationId.value = null;
    } else {
      expandedConversationId.value = id;
      void fetchRecordsForConversation(id);
    }
  }

  function isRecordsLoading(session: SessionVO) {
    return recordsLoadingMap[session.conversationId] ?? false;
  }

  function recordsOf(session: SessionVO) {
    return recordMap[session.conversationId] ?? [];
  }

  function handleSearch() {
    pageNum.value = 1;
    void fetchList();
  }

  return {
    keyword,
    pageNum,
    total,
    sessions,
    loading,
    loadError,
    expandedConversationId,
    recordMap,
    recordsLoadingMap,
    fetchList,
    toggleExpand,
    isRecordsLoading,
    recordsOf,
    handleSearch,
  };
}