<template>
  <div class="scale-answer-page">
    <div class="breadcrumb">
      <router-link to="/scale" class="breadcrumb-link">量表测评</router-link>
      <span class="breadcrumb-sep">›</span>
      <span class="breadcrumb-current">{{ scaleName }}</span>
    </div>

    <div class="answer-header">
      <h2 class="scale-title">{{ scaleName }}</h2>
      <div class="scale-meta">
        <el-tag type="info" size="small">{{ totalQuestions }}题</el-tag>
        <el-tag type="info" size="small">预计{{ estimatedMinutes }}分钟</el-tag>
      </div>
    </div>

    <div class="progress-bar">
      <div class="progress-label">答题进度：{{ answeredCount }} / {{ totalQuestions }}</div>
      <el-progress :percentage="progressPercent" :stroke-width="8" color="#6c63ff" />
    </div>

    <div class="question-card">
      <div class="question-number">第 {{ currentQuestion }} 题</div>
      <div class="question-text">{{ currentQuestionData.text }}</div>
      <div class="question-options">
        <div
          v-for="option in currentQuestionData.options"
          :key="option.value"
          class="option-item"
          :class="{ selected: answers[currentQuestion] === option.value }"
          @click="selectOption(option.value)"
        >
          <div class="option-radio" :class="{ checked: answers[currentQuestion] === option.value }"></div>
          <span class="option-text">{{ option.label }}</span>
        </div>
      </div>
    </div>

    <div class="nav-buttons">
      <el-button :disabled="currentQuestion <= 1" @click="currentQuestion--">上一题</el-button>
      <el-button v-if="currentQuestion < totalQuestions" type="primary" :disabled="!answers[currentQuestion]" @click="currentQuestion++">下一题</el-button>
      <el-button v-else type="success" :disabled="answeredCount < totalQuestions" @click="handleSubmit">提交答卷</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import { useRoute, useRouter } from "vue-router";

const route = useRoute();
const router = useRouter();
const scaleId = route.params.scaleId as string;
const scaleName = ref("SCL-90 症状自评量表");
const totalQuestions = ref(10);
const estimatedMinutes = ref(5);
const currentQuestion = ref(1);
const answers = ref<Record<number, number>>({});

const questions = [
  { text: "头痛", options: [{ label: "没有", value: 1 }, { label: "很轻", value: 2 }, { label: "中等", value: 3 }, { label: "偏重", value: 4 }, { label: "严重", value: 5 }] },
  { text: "神经过敏，心中不踏实", options: [{ label: "没有", value: 1 }, { label: "很轻", value: 2 }, { label: "中等", value: 3 }, { label: "偏重", value: 4 }, { label: "严重", value: 5 }] },
  { text: "头脑中有不必要的想法或字句盘旋", options: [{ label: "没有", value: 1 }, { label: "很轻", value: 2 }, { label: "中等", value: 3 }, { label: "偏重", value: 4 }, { label: "严重", value: 5 }] },
  { text: "头昏或昏倒", options: [{ label: "没有", value: 1 }, { label: "很轻", value: 2 }, { label: "中等", value: 3 }, { label: "偏重", value: 4 }, { label: "严重", value: 5 }] },
  { text: "对异性的兴趣减退", options: [{ label: "没有", value: 1 }, { label: "很轻", value: 2 }, { label: "中等", value: 3 }, { label: "偏重", value: 4 }, { label: "严重", value: 5 }] },
  { text: "对旁人责备求全", options: [{ label: "没有", value: 1 }, { label: "很轻", value: 2 }, { label: "中等", value: 3 }, { label: "偏重", value: 4 }, { label: "严重", value: 5 }] },
  { text: "感到别人能控制您的思想", options: [{ label: "没有", value: 1 }, { label: "很轻", value: 2 }, { label: "中等", value: 3 }, { label: "偏重", value: 4 }, { label: "严重", value: 5 }] },
  { text: "担心自己的衣饰整齐及仪态的端正", options: [{ label: "没有", value: 1 }, { label: "很轻", value: 2 }, { label: "中等", value: 3 }, { label: "偏重", value: 4 }, { label: "严重", value: 5 }] },
  { text: "容易哭泣", options: [{ label: "没有", value: 1 }, { label: "很轻", value: 2 }, { label: "中等", value: 3 }, { label: "偏重", value: 4 }, { label: "严重", value: 5 }] },
  { text: "感到孤独", options: [{ label: "没有", value: 1 }, { label: "很轻", value: 2 }, { label: "中等", value: 3 }, { label: "偏重", value: 4 }, { label: "严重", value: 5 }] },
];

const currentQuestionData = computed(() => questions[(currentQuestion.value - 1) % questions.length]);
const answeredCount = computed(() => Object.keys(answers.value).length);
const progressPercent = computed(() => Math.round((answeredCount.value / totalQuestions.value) * 100));

function selectOption(value: number) {
  answers.value[currentQuestion.value] = value;
}

function handleSubmit() {
  router.push("/scale/records");
}
</script>

<style scoped>
.scale-answer-page {
  padding: 24px;
  max-width: 700px;
  margin: 0 auto;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #999;
  margin-bottom: 16px;
}

.breadcrumb-link {
  color: #6c63ff;
  text-decoration: none;
}

.breadcrumb-current {
  color: #333;
  font-weight: 500;
}

.answer-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.scale-title {
  font-size: 20px;
  font-weight: 600;
  color: #1a1a2e;
}

.scale-meta {
  display: flex;
  gap: 6px;
}

.progress-bar {
  margin-bottom: 20px;
}

.progress-label {
  font-size: 13px;
  color: #666;
  margin-bottom: 6px;
}

.question-card {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  border: 1px solid #f0f0f0;
  margin-bottom: 20px;
}

.question-number {
  font-size: 13px;
  color: #6c63ff;
  font-weight: 600;
  margin-bottom: 8px;
}

.question-text {
  font-size: 16px;
  font-weight: 500;
  color: #333;
  margin-bottom: 16px;
}

.question-options {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.option-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-radius: 8px;
  border: 1px solid #e8e8e8;
  cursor: pointer;
  transition: all 0.2s;
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
  border: 2px solid #d9d9d9;
  flex-shrink: 0;
  transition: all 0.2s;
}

.option-radio.checked {
  border-color: #6c63ff;
  background: #6c63ff;
  box-shadow: inset 0 0 0 3px #fff;
}

.option-text {
  font-size: 14px;
  color: #333;
}

.nav-buttons {
  display: flex;
  justify-content: space-between;
}
</style>