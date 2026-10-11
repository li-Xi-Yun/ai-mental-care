<template>
  <section class="panel">
    <div class="panel-title"><span class="panel-icon">✍️</span>我的反馈</div>

    <div v-if="feedback && !editing" class="fb-summary">
      <div class="fb-stars">
        <el-rate :model-value="feedback.diagnosisScore ?? 0" disabled />
        <span class="fb-score-text">{{ feedback.diagnosisScore != null ? `${feedback.diagnosisScore} / 5` : "未评分" }}</span>
      </div>
      <div class="fb-agree-grid">
        <div class="fb-agree-item">
          <span>风险评估</span>
          <el-tag :type="agreeTagType(feedback.agreeRiskJudge)" size="small" effect="light">
            {{ agreeText(feedback.agreeRiskJudge) }}
          </el-tag>
        </div>
        <div class="fb-agree-item">
          <span>自助建议</span>
          <el-tag :type="agreeTagType(feedback.agreeSuggestionSelf)" size="small" effect="light">
            {{ agreeText(feedback.agreeSuggestionSelf) }}
          </el-tag>
        </div>
        <div class="fb-agree-item">
          <span>社会支持建议</span>
          <el-tag :type="agreeTagType(feedback.agreeSuggestionSocial)" size="small" effect="light">
            {{ agreeText(feedback.agreeSuggestionSocial) }}
          </el-tag>
        </div>
        <div class="fb-agree-item">
          <span>专业干预建议</span>
          <el-tag :type="agreeTagType(feedback.agreeSuggestionProfessional)" size="small" effect="light">
            {{ agreeText(feedback.agreeSuggestionProfessional) }}
          </el-tag>
        </div>
      </div>
      <div class="fb-use">
        <span class="kv-label">建议采纳</span>
        <strong>{{ useSuggestionText(feedback.useSuggestion) }}</strong>
      </div>
      <p v-if="feedback.feedbackContent" class="fb-content">"{{ feedback.feedbackContent }}"</p>
      <div class="fb-actions">
        <el-button type="primary" size="small" @click="$emit('startEdit')">编辑反馈</el-button>
      </div>
    </div>

    <div v-else class="fb-form">
      <div class="fb-field">
        <label class="fb-label">诊断评分</label>
        <el-rate v-model="form.diagnosisScore" :max="5" show-score :allow-half="false" />
      </div>
      <div class="fb-field">
        <label class="fb-label">建议认同</label>
        <div class="fb-check-list">
          <el-checkbox v-model="form.agreeRisk">认同风险评估</el-checkbox>
          <el-checkbox v-model="form.agreeSelf">认同自助调节建议</el-checkbox>
          <el-checkbox v-model="form.agreeSocial">认同社会支持建议</el-checkbox>
          <el-checkbox v-model="form.agreePro">认同专业干预建议</el-checkbox>
        </div>
      </div>
      <div class="fb-field">
        <label class="fb-label">建议采纳情况</label>
        <el-radio-group
          :model-value="form.useSuggestion ?? undefined"
          @change="$emit('useSuggestionChange', $event)"
        >
          <el-radio-button :value="0">未采纳</el-radio-button>
          <el-radio-button :value="1">尝试部分</el-radio-button>
          <el-radio-button :value="2">全部采纳</el-radio-button>
        </el-radio-group>
      </div>
      <div class="fb-field">
        <label class="fb-label">文字反馈</label>
        <el-input
          v-model="form.feedbackContent"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
          placeholder="写下你对本次诊断的看法或建议..."
        />
      </div>
      <div class="fb-actions">
        <el-button v-if="editing" @click="$emit('cancelEdit')">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="$emit('submitFeedback')">
          {{ feedback ? "保存修改" : "提交反馈" }}
        </el-button>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { AssessmentFeedbackVO } from "@/portal/api/conversation/emotion-diagnosis";

defineProps<{
  feedback: AssessmentFeedbackVO | null;
  editing: boolean;
  submitting: boolean;
  agreeTagType: (v?: number | null) => "success" | "warning" | "danger" | "info";
  agreeText: (v?: number | null) => string;
  useSuggestionText: (v?: number | null) => string;
  form: {
    diagnosisScore: number;
    agreeRisk: boolean;
    agreeSelf: boolean;
    agreeSocial: boolean;
    agreePro: boolean;
    useSuggestion: number | null;
    feedbackContent: string | null;
  };
}>();

defineEmits<{
  (e: "startEdit"): void;
  (e: "cancelEdit"): void;
  (e: "submitFeedback"): void;
  (e: "useSuggestionChange", val: string | number | boolean | undefined): void;
}>();
</script>

<style scoped>
.panel {
  background: #fff; border: 1px solid #ececf2; border-radius: 14px;
  padding: 20px; margin-bottom: 16px;
}

.panel-title {
  display: flex; align-items: center; gap: 8px;
  font-size: 15px; font-weight: 600; color: #1a1a2e; margin-bottom: 16px;
}

.panel-icon { font-size: 16px; }

.fb-summary { display: flex; flex-direction: column; gap: 12px; }
.fb-stars { display: flex; align-items: center; gap: 8px; }
.fb-score-text { font-size: 13px; color: #fa8c16; font-weight: 600; }
.fb-agree-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.fb-agree-item { display: flex; align-items: center; justify-content: space-between; font-size: 13px; color: #555; }
.fb-use { display: flex; align-items: center; gap: 8px; font-size: 13px; }
.fb-content { font-size: 13px; color: #888; font-style: italic; margin: 4px 0; }
.fb-actions { display: flex; gap: 8px; margin-top: 8px; }

.fb-form { display: flex; flex-direction: column; gap: 16px; }
.fb-field { display: flex; flex-direction: column; gap: 8px; }
.fb-label { font-size: 13px; color: #666; font-weight: 500; }
.fb-check-list { display: flex; flex-wrap: wrap; gap: 16px; }

.kv-label { font-size: 12px; color: #9aa0b5; }
</style>