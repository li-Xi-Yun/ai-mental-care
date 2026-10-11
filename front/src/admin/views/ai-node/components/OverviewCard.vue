<template>
  <div class="card overview-card">
    <div class="overview-head">
      <div class="node-title">
        <el-icon class="node-icon"><Cpu /></el-icon>
        <h1 class="node-name">{{ nodeName || "未命名节点" }}</h1>
        <el-tag :type="enabledTagType" size="small" effect="light">{{ enabledLabel }}</el-tag>
        <el-tag type="info" size="small" effect="plain">版本 v{{ version ?? "-" }}</el-tag>
      </div>
      <div class="overview-actions">
        <el-button @click="emit('reload')" :loading="loading">
          <el-icon class="btn-icon"><Refresh /></el-icon>刷新
        </el-button>
        <el-button type="primary" :loading="saving" @click="emit('save')">
          <el-icon class="btn-icon"><Save /></el-icon>保存配置
        </el-button>
      </div>
    </div>
    <div class="overview-meta">
      <span class="meta-item"><span class="meta-label">nodeKey</span><code class="meta-mono">{{ nodeKey || "-" }}</code></span>
      <span class="meta-dot">·</span>
      <span class="meta-item"><span class="meta-label">分组</span>{{ nodeGroup || "-" }}</span>
      <span class="meta-dot">·</span>
      <span class="meta-item"><span class="meta-label">模型</span>{{ modelLabel }}</span>
      <span class="meta-dot">·</span>
      <span class="meta-item"><span class="meta-label">排序</span>{{ sort }}</span>
      <span class="meta-dot">·</span>
      <span class="meta-item"><span class="meta-label">创建</span>{{ createdTime || "-" }}</span>
      <span class="meta-dot">·</span>
      <span class="meta-item"><span class="meta-label">更新</span>{{ updatedTime || "-" }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
const props = defineProps<{
  nodeName?: string;
  enabledTagType: "success" | "info";
  enabledLabel: string;
  version?: number | null;
  nodeKey?: string | null;
  nodeGroup?: string;
  modelLabel?: string;
  sort?: number;
  createdTime?: string | null;
  updatedTime?: string | null;
  loading?: boolean;
  saving?: boolean;
}>();

const emit = defineEmits<{
  (e: "reload"): void;
  (e: "save"): void;
}>();

void props;
</script>

<style scoped>
.card {
  background: #ffffff;
  border: 1px solid var(--admin-border);
  border-radius: var(--admin-radius-card);
  padding: 20px 22px;
  box-shadow: var(--admin-shadow-card);
}

.overview-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.node-title {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.node-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  font-size: 20px;
  flex-shrink: 0;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
}

.node-name {
  font-size: 20px;
  font-weight: 700;
  color: #1e293b;
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.overview-actions {
  display: flex;
  gap: 8px;
}

.btn-icon {
  margin-right: 4px;
}

.overview-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--admin-border);
  font-size: 12px;
  color: #475569;
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.meta-label {
  color: var(--admin-text-muted);
}

.meta-mono {
  font-family: ui-monospace, "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  background: #f1f5f9;
  border-radius: 4px;
  padding: 1px 6px;
  color: #4f46e5;
}

.meta-dot {
  color: #cbd5e1;
}
</style>