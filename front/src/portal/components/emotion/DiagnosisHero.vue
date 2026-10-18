<template>
  <header class="es-page-header">
    <div>
      <h2 class="es-title">心理评估 - 详情</h2>
      <p class="es-subtitle">
        {{ roundNumText }} · 创建 {{ formatDateTime(diagnosis?.createdTime) }} · 更新
        {{ formatDateTime(diagnosis?.updatedTime) }}
      </p>
    </div>
    <button type="button" class="es-btn-primary" @click="$emit('scrollToFeedback')">
      📝 反馈此报告
    </button>
  </header>
</template>

<script setup lang="ts">
import type { EmotionDiagnosisVO } from "@/portal/api/conversation/emotion-diagnosis";

defineProps<{
  diagnosis: EmotionDiagnosisVO | null;
  roundNumText: string;
  formatDateTime: (v?: string) => string;
  formatPercent: (v?: number | null, digits?: number) => string;
}>();

defineEmits<{
  (e: "scrollToFeedback"): void;
}>();
</script>