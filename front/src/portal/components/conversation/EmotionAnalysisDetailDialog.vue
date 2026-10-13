<template>
  <el-dialog v-model="visible" title="本轮对话详情" width="560px" destroy-on-close>
    <div v-loading="loading" class="detail-content">
      <el-empty v-if="!loading && !detail" description="暂无对话详情" />
      <template v-else-if="detail">
        <section v-if="detail.userContent" class="detail-section">
          <div class="detail-title">用户消息</div>
          <div class="detail-text">{{ detail.userContent }}</div>
        </section>
        <section v-if="detail.modelContent" class="detail-section">
          <div class="detail-title">AI 回复</div>
          <div class="detail-text">{{ detail.modelContent }}</div>
        </section>
        <section v-if="detail.thinkContent" class="detail-section">
          <div class="detail-title">思考过程</div>
          <div class="detail-text">{{ detail.thinkContent }}</div>
        </section>
      </template>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from "vue";
import { getEmotionAnalysisDetail, type EmotionAnalysisDetailVO } from "@/portal/api/conversation/emotion-analysis";

const props = defineProps<{
  modelValue: boolean;
  analysisId: string | null;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void;
}>();

const visible = ref(props.modelValue);
const loading = ref(false);
const detail = ref<EmotionAnalysisDetailVO | null>(null);

watch(
  () => props.modelValue,
  (value) => {
    visible.value = value;
    if (value && props.analysisId) void loadDetail(props.analysisId);
  },
);

watch(visible, (value) => {
  emit("update:modelValue", value);
});

async function loadDetail(analysisId: string): Promise<void> {
  loading.value = true;
  detail.value = null;
  try {
    const res = await getEmotionAnalysisDetail(analysisId);
    detail.value = res.data?.data ?? null;
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.detail-content {
  min-height: 120px;
}

.detail-section + .detail-section {
  margin-top: 16px;
}

.detail-title {
  margin-bottom: 6px;
  color: #6c63ff;
  font-size: 12px;
  font-weight: 600;
}

.detail-text {
  padding: 10px 12px;
  border-radius: 8px;
  background: #f8f9fc;
  color: #4b5563;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
