import { ref, computed } from "vue";
import { ElMessage } from "element-plus";

export function useAnswerTimer() {
  const timeLimit = ref(0);
  const remainingSeconds = ref(0);
  const timerId = ref<number | null>(null);
  const timeWarning = ref(false);

  const formattedTime = computed(() => {
    const total = Math.max(0, remainingSeconds.value);
    const h = Math.floor(total / 3600);
    const m = Math.floor((total % 3600) / 60);
    const s = total % 60;
    const mm = String(m).padStart(2, "0");
    const ss = String(s).padStart(2, "0");
    return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`;
  });

  function startTimer(limit: number, onTimeout: () => void) {
    if (limit <= 0) return;
    timeLimit.value = limit;
    remainingSeconds.value = limit;
    timerId.value = window.setInterval(() => {
      remainingSeconds.value -= 1;
      timeWarning.value = remainingSeconds.value <= 60;
      if (remainingSeconds.value <= 0) {
        stopTimer();
        ElMessage.warning("作答时间已到，正在自动提交…");
        onTimeout();
      }
    }, 1000);
  }

  function stopTimer() {
    if (timerId.value !== null) window.clearInterval(timerId.value);
    timerId.value = null;
  }

  return {
    timeLimit,
    remainingSeconds,
    timeWarning,
    formattedTime,
    startTimer,
    stopTimer,
  };
}