import { ref } from "vue";
import { getSession, setSession, removeSession } from "@/shared/utils/storage";

const ANSWER_STORAGE_KEY = "scale_answer_draft_v1";

interface AnswerEntry {
  optionIds: number[];
  answerText?: string;
  spendSeconds?: number;
}

interface AnswerDraft {
  scaleId: number;
  recordId: number;
  currentIndex: number;
  answers: Record<number, AnswerEntry>;
}

export function useAnswerDraft(scaleId: number) {
  const answers = ref<Record<number, AnswerEntry>>({});
  const currentIndex = ref(0);

  function restoreDraft(expectedRecordId: number) {
    try {
      const draft = getSession<AnswerDraft>(ANSWER_STORAGE_KEY);
      if (!draft || draft.scaleId !== scaleId || draft.recordId !== expectedRecordId) {
        return false;
      }
      currentIndex.value = draft.currentIndex ?? 0;
      answers.value = draft.answers ?? {};
      return true;
    } catch {
      return false;
    }
  }

  function persistDraft(recordId: number) {
    setSession(ANSWER_STORAGE_KEY, {
      scaleId,
      recordId,
      currentIndex: currentIndex.value,
      answers: answers.value,
    });
  }

  function clearDraft() {
    removeSession(ANSWER_STORAGE_KEY);
  }

  return {
    answers,
    currentIndex,
    restoreDraft,
    persistDraft,
    clearDraft,
  };
}