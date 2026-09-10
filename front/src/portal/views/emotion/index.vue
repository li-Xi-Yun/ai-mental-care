<template>
  <div class="diagnosis-page">
    <div class="diagnosis-header">
      <h2 class="page-title">心理诊断</h2>
      <div class="filter-group">
        <el-radio-group v-model="filterType" size="small">
          <el-radio-button value="all">全部</el-radio-button>
          <el-radio-button value="attention">需关注</el-radio-button>
          <el-radio-button value="normal">正常</el-radio-button>
        </el-radio-group>
      </div>
    </div>

    <div class="diagnosis-list">
      <div
        v-for="session in filteredSessions"
        :key="session.id"
        class="session-card"
      >
        <div
          class="session-header"
          :class="{ expanded: expandedSessionId === session.id }"
          @click="toggleSession(session.id)"
        >
          <span class="session-icon">{{ session.icon }}</span>
          <div class="session-info">
            <div class="session-title">{{ session.title }}</div>
            <div class="session-meta">
              最近诊断：{{ session.lastDiagnosis }} | 共{{ session.diagnosisCount }}条诊断记录
            </div>
          </div>
          <el-tag :type="session.levelType" size="small" effect="light">{{ session.level }}</el-tag>
          <el-icon class="expand-icon" :class="{ rotated: expandedSessionId === session.id }">
            <ArrowDown />
          </el-icon>
        </div>

        <transition name="expand">
          <div v-show="expandedSessionId === session.id" class="session-body">
            <div
              v-for="diagnosis in session.diagnoses"
              :key="diagnosis.id"
              class="diagnosis-item"
            >
              <div
                class="diagnosis-header"
                @click="toggleDiagnosis(diagnosis.id)"
              >
                <span class="diagnosis-icon">📋</span>
                <div class="diagnosis-info">
                  <span class="diagnosis-title">{{ diagnosis.title }}</span>
                  <span class="diagnosis-time">{{ diagnosis.time }}</span>
                </div>
                <el-tag :type="diagnosis.levelType" size="small" effect="light">{{ diagnosis.level }}</el-tag>
                <el-icon class="expand-icon small" :class="{ rotated: expandedDiagnosisId === diagnosis.id }">
                  <ArrowDown />
                </el-icon>
              </div>

              <transition name="expand">
                <div v-show="expandedDiagnosisId === diagnosis.id" class="diagnosis-body">
                  <div class="detail-row">
                    <span class="detail-label">主要情绪：</span>
                    <span class="detail-value">{{ diagnosis.emotions }}</span>
                  </div>
                  <div class="detail-row">
                    <span class="detail-label">触发因素：</span>
                    <span class="detail-value">{{ diagnosis.triggers }}</span>
                  </div>
                  <div class="detail-row">
                    <span class="detail-label">AI建议：</span>
                    <span class="detail-value advice">{{ diagnosis.suggestion }}</span>
                  </div>
                </div>
              </transition>
            </div>
          </div>
        </transition>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import { useRouter } from "vue-router";
import { ArrowDown } from "@element-plus/icons-vue";
import { DIAGNOSIS_FILTERS } from "@/shared/api/config";

const router = useRouter();

const filterType = ref<"all" | "attention" | "normal">("all");
const expandedSessionId = ref<string | null>("1");
const expandedDiagnosisId = ref<string | null>("1-1");

interface Diagnosis {
  id: string;
  title: string;
  time: string;
  level: string;
  levelType: "success" | "warning" | "danger" | "info";
  emotions: string;
  triggers: string;
  suggestion: string;
}

interface Session {
  id: string;
  icon: string;
  title: string;
  lastDiagnosis: string;
  diagnosisCount: number;
  level: string;
  levelType: "success" | "warning" | "danger" | "info";
  filterCategory: "attention" | "normal";
  diagnoses: Diagnosis[];
}

const sessionList = ref<Session[]>([
  {
    id: "1",
    icon: "💬",
    title: "心情困扰与工作压力",
    lastDiagnosis: "2026-09-08",
    diagnosisCount: 3,
    level: "轻度焦虑",
    levelType: "warning",
    filterCategory: "attention",
    diagnoses: [
      {
        id: "1-1",
        title: "诊断 #1 — 轻度焦虑倾向",
        time: "2026-09-08 14:30",
        level: "轻度",
        levelType: "warning",
        emotions: "焦虑(45%)、紧张(30%)、低落(25%)",
        triggers: "工作截止日期临近、睡眠不足",
        suggestion: "建议每日进行10分钟深呼吸放松训练，保持规律作息",
      },
      {
        id: "1-2",
        title: "诊断 #2 — 情绪波动正常",
        time: "2026-09-06 09:15",
        level: "正常",
        levelType: "success",
        emotions: "平静(50%)、轻微焦虑(30%)、期待(20%)",
        triggers: "日常事务",
        suggestion: "情绪状态良好，继续保持积极的生活节奏",
      },
      {
        id: "1-3",
        title: "诊断 #3 — 工作压力相关",
        time: "2026-09-03 20:00",
        level: "关注",
        levelType: "info",
        emotions: "压力(40%)、疲惫(35%)、焦虑(25%)",
        triggers: "连续加班、缺乏运动",
        suggestion: "建议合理安排工作时间，增加户外活动，每周至少3次30分钟运动",
      },
    ],
  },
  {
    id: "2",
    icon: "💭",
    title: "睡眠质量讨论",
    lastDiagnosis: "2026-09-05",
    diagnosisCount: 2,
    level: "正常",
    levelType: "success",
    filterCategory: "normal",
    diagnoses: [
      {
        id: "2-1",
        title: "诊断 #1 — 睡眠质量改善",
        time: "2026-09-05 22:00",
        level: "正常",
        levelType: "success",
        emotions: "平静(60%)、放松(30%)、轻微担忧(10%)",
        triggers: "睡前使用手机",
        suggestion: "建议睡前1小时放下手机，尝试冥想或阅读帮助入睡",
      },
      {
        id: "2-2",
        title: "诊断 #2 — 入睡困难",
        time: "2026-09-02 23:30",
        level: "轻度",
        levelType: "warning",
        emotions: "焦虑(35%)、烦躁(35%)、疲惫(30%)",
        triggers: "工作思绪无法停止",
        suggestion: "建议尝试4-7-8呼吸法：吸气4秒、屏息7秒、呼气8秒",
      },
    ],
  },
  {
    id: "3",
    icon: "🌙",
    title: "人际关系困扰",
    lastDiagnosis: "2026-09-01",
    diagnosisCount: 1,
    level: "轻度",
    levelType: "warning",
    filterCategory: "attention",
    diagnoses: [
      {
        id: "3-1",
        title: "诊断 #1 — 社交焦虑倾向",
        time: "2026-09-01 16:00",
        level: "轻度",
        levelType: "warning",
        emotions: "焦虑(40%)、回避(30%)、孤独(30%)",
        triggers: "同事评价、社交场合",
        suggestion: "建议逐步暴露法，从小范围社交开始练习，增强社交自信",
      },
    ],
  },
]);

const filteredSessions = computed(() => {
  if (filterType.value === "all") return sessionList.value;
  return sessionList.value.filter((s) => s.filterCategory === filterType.value);
});

function toggleSession(id: string) {
  expandedSessionId.value = expandedSessionId.value === id ? null : id;
  expandedDiagnosisId.value = null;
}

function toggleDiagnosis(id: string) {
  expandedDiagnosisId.value = expandedDiagnosisId.value === id ? null : id;
}
</script>

<style scoped>
.diagnosis-page {
  padding: 24px;
  max-width: 900px;
  margin: 0 auto;
}

.diagnosis-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #1a1a2e;
}

.diagnosis-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.session-card {
  background: #fff;
  border-radius: 10px;
  border: 1px solid #f0f0f0;
  overflow: hidden;
  transition: box-shadow 0.2s;
}

.session-card:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.session-header {
  padding: 14px 18px;
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  transition: background 0.2s;
}

.session-header:hover {
  background: #f8f7ff;
}

.session-header.expanded {
  background: #f8f7ff;
  border-bottom: 1px solid #f0f0f0;
}

.session-icon {
  font-size: 20px;
  flex-shrink: 0;
}

.session-info {
  flex: 1;
  min-width: 0;
}

.session-title {
  font-size: 15px;
  font-weight: 600;
  color: #333;
}

.session-meta {
  font-size: 12px;
  color: #999;
  margin-top: 2px;
}

.expand-icon {
  color: #999;
  transition: transform 0.3s;
  flex-shrink: 0;
}

.expand-icon.rotated {
  transform: rotate(180deg);
}

.expand-icon.small {
  font-size: 12px;
}

.session-body {
  padding: 12px 18px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.diagnosis-item {
  background: #fafafa;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
  overflow: hidden;
}

.diagnosis-header {
  padding: 10px 14px;
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  transition: background 0.2s;
}

.diagnosis-header:hover {
  background: #f0eeff;
}

.diagnosis-icon {
  font-size: 14px;
  flex-shrink: 0;
}

.diagnosis-info {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.diagnosis-title {
  font-size: 13px;
  font-weight: 500;
  color: #333;
}

.diagnosis-time {
  font-size: 11px;
  color: #999;
  white-space: nowrap;
}

.diagnosis-body {
  padding: 10px 14px 12px;
  border-top: 1px solid #f0f0f0;
  font-size: 13px;
  color: #666;
  line-height: 1.8;
}

.detail-row {
  margin-bottom: 4px;
}

.detail-label {
  font-weight: 500;
  color: #555;
}

.detail-value.advice {
  color: #6c63ff;
}

.expand-enter-active,
.expand-leave-active {
  transition: all 0.3s ease;
  overflow: hidden;
}

.expand-enter-from,
.expand-leave-to {
  opacity: 0;
  max-height: 0;
}

.expand-enter-to,
.expand-leave-from {
  max-height: 500px;
}
</style>