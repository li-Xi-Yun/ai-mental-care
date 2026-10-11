<template>
  <aside class="info-panel">
    <div class="panel-head">
      <div class="panel-title">
        <el-icon class="panel-title-icon"><Collection /></el-icon>
        <span>量表信息</span>
      </div>
      <div class="panel-actions">
        <el-button text class="mini-btn" :loading="loading" @click="emit('reload')">
          <el-icon><RefreshRight /></el-icon>
        </el-button>
        <el-button text class="mini-btn" @click="emit('edit')">
          <el-icon><Edit /></el-icon>编辑
        </el-button>
      </div>
    </div>

    <div class="panel-body">
      <div class="scale-head">
        <h2 class="scale-title">{{ scaleName }}</h2>
        <el-tag :type="detail.status === 1 ? 'success' : 'danger'" size="small" effect="light">
          {{ detail.status === 1 ? "启用" : "禁用" }}
        </el-tag>
      </div>

      <div class="curr-version" v-if="currentVersion">
        <span class="cv-label">当前版本</span>
        <span class="cv-value">{{ currentVersion.versionNo }}</span>
        <el-tag v-if="currentVersion.isCurrent" type="success" size="small" effect="light">生效中</el-tag>
      </div>

      <div class="stat-grid">
        <div class="stat-item">
          <div class="stat-num">{{ detail.questionCount ?? 0 }}</div>
          <div class="stat-label">题目数</div>
        </div>
        <div class="stat-item">
          <div class="stat-num">{{ detail.dimensionCount ?? 0 }}</div>
          <div class="stat-label">维度数</div>
        </div>
        <div class="stat-item">
          <div class="stat-num">{{ versionCount }}</div>
          <div class="stat-label">版本数</div>
        </div>
      </div>

      <div class="info-block" v-if="scaleDescription">
        <div class="info-block-label">量表描述 / 指导语</div>
        <p class="info-block-text">{{ scaleDescription }}</p>
      </div>

      <div class="info-meta">
        <div class="meta-row">
          <span class="meta-label">分类</span>
          <span class="meta-value" v-if="detail.scaleCategoryName">{{ detail.scaleCategoryName }}</span>
          <span class="meta-value muted" v-else>未分类</span>
        </div>
        <div class="meta-row">
          <span class="meta-label">创建人</span>
          <span class="meta-value">{{ detail.createdByName || "—" }}</span>
        </div>
        <div class="meta-row">
          <span class="meta-label">创建时间</span>
          <span class="meta-value">{{ formatTime(detail.createdTime) }}</span>
        </div>
        <div class="meta-row">
          <span class="meta-label">最近更新</span>
          <span class="meta-value">{{ formatTime(detail.updatedTime) }}</span>
        </div>
      </div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { Collection, Edit, RefreshRight } from "@element-plus/icons-vue";
import type { AdminScaleDetailVO, AdminScaleVersionVO } from "@/admin/api/scale/types";

defineProps<{
  detail: AdminScaleDetailVO;
  scaleName: string;
  scaleDescription: string;
  currentVersion: AdminScaleVersionVO | null;
  versionCount: number;
  loading: boolean;
  formatTime: (v?: string | number) => string;
}>();
const emit = defineEmits<{
  (e: "reload"): void;
  (e: "edit"): void;
}>();
</script>

<style scoped>
/* ==================== 左侧量表信息卡 ==================== */
.info-panel {
  width: 302px;
  flex-shrink: 0;
  background: #fff;
  border: 1px solid var(--admin-border);
  border-radius: var(--admin-radius-card);
  box-shadow: var(--admin-shadow-card);
  overflow: hidden;
  position: sticky;
  top: 0;
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px 12px;
  border-bottom: 1px solid var(--admin-border);
}
.panel-title {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 14px;
  font-weight: 600;
  color: var(--admin-text-primary);
}
.panel-title-icon {
  color: var(--admin-primary);
  font-size: 16px;
}
.panel-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}
.mini-btn {
  color: var(--admin-primary);
  font-size: 12px;
  padding: 4px 8px;
  height: auto;
}
.panel-body {
  padding: 16px;
}
.scale-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}
.scale-title {
  font-size: 19px;
  font-weight: 700;
  color: var(--admin-text-primary);
  line-height: 1.3;
  margin: 0;
  word-break: break-all;
}
.curr-version {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #f8faff;
  border: 1px solid #e0e7ff;
  border-radius: 8px;
  margin-bottom: 14px;
}
.cv-label {
  font-size: 12px;
  color: #94a3b8;
}
.cv-value {
  font-size: 13px;
  font-weight: 600;
  color: var(--admin-primary);
}

/* 三列统计条与全局四列 stat-grid 不同，保留页面特有定义 */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin-bottom: 14px;
}
.stat-item {
  text-align: center;
  padding: 10px 4px;
  background: var(--admin-bg);
  border: 1px solid var(--admin-border);
  border-radius: var(--admin-radius-control);
}
.stat-num {
  font-size: 22px;
  font-weight: 700;
  color: var(--admin-primary);
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}
.stat-label {
  margin-top: 2px;
  font-size: 11px;
  color: var(--admin-text-muted);
}

.info-block {
  margin-bottom: 14px;
}
.info-block-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--admin-text-secondary);
  margin-bottom: 4px;
}
.info-block-text {
  font-size: 12px;
  color: var(--admin-text-secondary);
  line-height: 1.7;
  margin: 0;
  max-height: 120px;
  overflow-y: auto;
}

.info-meta {
  border-top: 1px dashed var(--admin-border-strong);
  padding-top: 12px;
}
.meta-row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  margin-bottom: 7px;
}
.meta-label {
  width: 60px;
  flex-shrink: 0;
  color: var(--admin-text-muted);
}
.meta-value {
  color: var(--admin-text-primary);
  word-break: break-all;
}
.meta-value.muted {
  color: #cbd5e1;
}
</style>
