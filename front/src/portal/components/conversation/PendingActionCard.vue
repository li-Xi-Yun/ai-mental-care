<template>
  <div class="pending-card" :class="`status-${act.status}`">
    <template v-if="act.actionType === 1">
      <div class="pc-head">
        <span class="pc-badge">量表推荐</span>
        <span class="pc-status">{{ statusText }}</span>
      </div>
      <div class="pc-title">{{ act.actionData?.scaleName || "心理测评量表" }}</div>
      <div class="pc-meta">
        <span v-if="act.actionData?.totalQuestions">共 {{ act.actionData.totalQuestions }} 题</span>
        <span v-if="act.actionData?.recommendReason" class="pc-reason">推荐理由：{{ act.actionData.recommendReason }}</span>
      </div>

      <!-- 等待作答：可进入答题 / 保存记录 / 提交完成 -->
      <div v-if="act.status === 0" class="pc-actions">
        <el-button size="small" type="primary" :loading="busy === 'start'" @click="emit('start', act)">开始作答</el-button>
        <el-button size="small" plain :loading="busy === 'done'" @click="emit('answered', act, 1)">已完成</el-button>
        <el-button size="small" text type="info" @click="emit('answered', act, 0)">放弃</el-button>
      </div>

      <!-- 已响应：展示分析结论（如有） -->
      <div v-else-if="act.status === 2" class="pc-result">
        <span>测评已完成，AI 已回复解读结果</span>
      </div>
      <div v-else class="pc-result">
        <span>{{ statusText }}</span>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import type { PendingActionItem } from "@/portal/api/conversation/types";

const props = defineProps<{
  /** 待处理交互（当前仅量表推荐 actionType=1 有卡片形态） */
  act: PendingActionItem;
  /** 当前卡片正在执行的动作（start=进入作答，done=提交/放弃），用于按钮 loading */
  busy?: "start" | "done";
}>();

const emit = defineEmits<{
  (e: "start", act: PendingActionItem): void;
  (e: "answered", act: PendingActionItem, answered: number): void;
}>();

/** 待处理卡片状态文案 */
const statusText = computed(() => {
  switch (props.act.status) {
    case 0:
      return "等待作答";
    case 1:
      return "分析中…";
    case 2:
      return "已解读";
    case 3:
      return "已取消";
    case 4:
      return "已过期";
    default:
      return "未知";
  }
});
</script>

<style scoped>
.pending-card {
  background: #fff;
  border: 1px solid #e5e7f0;
  border-radius: 14px;
  padding: 14px 16px;
  margin: 12px 0 4px 4px;
  box-shadow: 0 2px 10px rgba(108, 99, 255, 0.08);
}

.pc-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.pc-badge {
  font-size: 11px;
  color: #6c63ff;
  background: #f0eeff;
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 600;
}

.pc-status {
  font-size: 12px;
  color: #999;
}

.pc-title {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a2e;
  margin-bottom: 6px;
}

.pc-meta {
  font-size: 12px;
  color: #888;
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 12px;
}

.pc-reason {
  color: #6c63ff;
}

.pc-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.pc-result {
  font-size: 13px;
  color: #52c41a;
  padding: 8px 0 2px;
}

.pending-card.status-3 .pc-result,
.pending-card.status-4 .pc-result {
  color: #999;
}
</style>
