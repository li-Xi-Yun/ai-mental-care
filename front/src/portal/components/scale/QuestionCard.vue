<template>
  <div class="question-card">
    <div class="q-header">
      <span class="q-number">
        第 <b>{{ currentIndex + 1 }}</b> / {{ totalCount }} 题
      </span>
      <span class="q-required" :class="{ unanswered: isRequired && !answerReady }">
        {{ isRequired ? (answerReady ? "必答题 · 已作答" : "必答题 · 未作答") : "选答题" }}
      </span>
    </div>

    <h3 class="q-text">{{ title }}</h3>

    <!-- 单选 -->
    <div v-if="questionType === 1" class="option-list">
      <div
        v-for="option in options"
        :key="option.optionId"
        class="option-item"
        :class="{ selected: selectedIds.includes(option.optionId) }"
        @click="handleSelectSingle(option.optionId)"
      >
        <span class="option-radio" :class="{ checked: selectedIds.includes(option.optionId) }"></span>
        <span class="option-text">{{ option.optionText }}</span>
      </div>
    </div>

    <!-- 多选 -->
    <div v-else-if="questionType === 2" class="option-list">
      <div
        v-for="option in options"
        :key="option.optionId"
        class="option-item multi"
        :class="{ selected: selectedIds.includes(option.optionId) }"
        @click="handleToggleMulti(option.optionId)"
      >
        <span class="option-checkbox" :class="{ checked: selectedIds.includes(option.optionId) }">
          <el-icon v-if="selectedIds.includes(option.optionId)" :size="12"><Check /></el-icon>
        </span>
        <span class="option-text">{{ option.optionText }}</span>
      </div>
    </div>

    <!-- 填空 -->
    <div v-else class="fill-input">
      <el-input
        :model-value="answerText"
        type="textarea"
        :rows="3"
        maxlength="500"
        show-word-limit
        resize="none"
        placeholder="请输入您的回答…"
        @update:model-value="$emit('update:answerText', $event)"
      />
    </div>

    <p v-if="copyrightInfo" class="copyright">{{ copyrightInfo }}</p>
  </div>
</template>

<script setup lang="ts">
import { Check } from "@element-plus/icons-vue";

interface Option {
  optionId: number;
  optionText: string;
}

defineProps<{
  currentIndex: number;
  totalCount: number;
  title: string;
  questionType: number;
  options: Option[];
  selectedIds: number[];
  answerText: string;
  isRequired: boolean;
  answerReady: boolean;
  copyrightInfo?: string;
}>();

const emit = defineEmits<{
  (e: "update:answerText", val: string): void;
  (e: "selectSingle", optionId: number): void;
  (e: "toggleMulti", optionId: number): void;
}>();

function handleSelectSingle(optionId: number) {
  emit("selectSingle", optionId);
}

function handleToggleMulti(optionId: number) {
  emit("toggleMulti", optionId);
}
</script>

<style scoped>
.question-card {
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 16px;
  padding: 24px 24px 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.q-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.q-number {
  font-size: 13px;
  color: #999;
}

.q-number b {
  color: #6c63ff;
  font-size: 16px;
}

.q-required {
  font-size: 12px;
  color: #52c41a;
  padding: 2px 10px;
  border-radius: 12px;
  background: #f6ffed;
}

.q-required.unanswered {
  color: #ff4d4f;
  background: #fff1f0;
}

.q-text {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a2e;
  line-height: 1.7;
  margin: 0 0 20px;
}

.option-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.option-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 13px 16px;
  border: 1.5px solid #e8e8e8;
  border-radius: 12px;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s, transform 0.15s;
}

.option-item:hover {
  border-color: #6c63ff;
  background: #f8f7ff;
}

.option-item.selected {
  border-color: #6c63ff;
  background: #f0eeff;
}

.option-radio {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: 2px solid #d0d0d0;
  flex-shrink: 0;
  transition: all 0.2s;
}

.option-radio.checked {
  border-color: #6c63ff;
  background: #6c63ff;
  box-shadow: inset 0 0 0 3px #fff;
}

.option-checkbox {
  width: 18px;
  height: 18px;
  border-radius: 4px;
  border: 2px solid #d0d0d0;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  transition: all 0.2s;
}

.option-checkbox.checked {
  background: #6c63ff;
  border-color: #6c63ff;
}

.option-text {
  font-size: 14px;
  color: #333;
  line-height: 1.5;
  flex: 1;
}

.fill-input {
  margin-bottom: 4px;
}

.copyright {
  margin-top: 16px;
  font-size: 11px;
  color: #bbb;
  text-align: center;
}
</style>