<template>
  <aside class="category-panel">
    <div class="category-header">
      <span class="category-title">
        <el-icon :size="14"><FolderOpened /></el-icon>
        文件分类
      </span>
      <el-button text size="small" class="category-config-btn" @click="emit('manage')">管理</el-button>
    </div>
    <div
      class="category-all"
      :class="{ active: activeCategoryId === undefined }"
      @click="emit('select', undefined)"
    >
      <el-icon :size="13"><Folder /></el-icon>
      <span class="node-name">全部文件</span>
      <span class="node-count">{{ totalFiles }}</span>
    </div>
    <el-tree
      class="category-tree"
      :data="categoryTree"
      :props="categoryTreeProps"
      node-key="id"
      default-expand-all
      :expand-on-click-node="false"
      :highlight-current="activeCategoryId !== undefined"
      :current-node-key="activeCategoryId"
      @node-click="handleTreeClick"
    >
      <template #default="{ data }">
        <div class="category-node-inner">
          <el-icon :size="13"><Folder /></el-icon>
          <span class="node-name">{{ data.categoryName }}</span>
          <span v-if="data.defaultType === 1" class="node-default">默认</span>
          <span v-if="data.fileCount" class="node-count">{{ data.fileCount }}</span>
        </div>
      </template>
    </el-tree>
    <el-button
      v-if="!categoryLoading && !categoryTree.length"
      class="category-add-tip"
      text
      type="primary"
      size="small"
      @click="emit('add')"
    >
      + 新增分类
    </el-button>
  </aside>
</template>

<script setup lang="ts">
import { Folder, FolderOpened } from "@element-plus/icons-vue";
import type { CategoryNode } from "../useFileList";

defineProps<{
  categoryTree: CategoryNode[];
  categoryLoading: boolean;
  /** 当前选中的分类（undefined 表示「全部文件」） */
  activeCategoryId?: number;
  /** 全部文件数量 */
  totalFiles: number;
  categoryTreeProps: { children: string; label: string };
}>();

const emit = defineEmits<{
  (e: "select", categoryId: number | undefined): void;
  (e: "manage"): void;
  (e: "add"): void;
}>();

function handleTreeClick(data: CategoryNode) {
  emit("select", data.id);
}
</script>

<style scoped>
.category-panel {
  width: 180px;
  flex-shrink: 0;
  background: #fff;
  border: 1px solid var(--admin-border);
  border-radius: var(--admin-radius-card);
  padding: 14px 12px;
  box-shadow: var(--admin-shadow-card);
  display: flex;
  flex-direction: column;
}

.category-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.category-title {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
}

.category-config-btn {
  color: #6366f1;
  font-weight: 500;
}

.category-all {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  border-radius: 8px;
  font-size: 12.5px;
  color: #1e293b;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  margin-bottom: 2px;
}

.category-all:hover {
  background: #f1f5f9;
}

.category-all.active {
  background: #eef2ff;
  color: #6366f1;
}

.category-tree {
  flex: 1;
  overflow-y: auto;
  --el-tree-node-hover-bg-color: #f8fafc;
}

.category-tree :deep(.el-tree-node__content) {
  height: 32px;
  border-radius: 8px;
  margin-bottom: 1px;
}

.category-tree :deep(.el-tree-node.is-current > .el-tree-node__content) {
  background: #eef2ff;
  color: #6366f1;
}

.category-node-inner {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 5px;
  min-width: 0;
  padding-right: 4px;
}

.node-name {
  flex: 1;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.node-count {
  font-size: 10.5px;
  color: #94a3b8;
  background: #f1f5f9;
  border-radius: 8px;
  padding: 0 6px;
  line-height: 1.6;
  flex-shrink: 0;
}

.category-all.active .node-count,
.category-tree :deep(.el-tree-node.is-current) .node-count {
  background: #e0e7ff;
  color: #6366f1;
}

.node-default {
  font-size: 10px;
  background: #eef2ff;
  color: #6366f1;
  border-radius: 4px;
  padding: 0 4px;
  line-height: 1.6;
  flex-shrink: 0;
}

.category-add-tip {
  width: 100%;
  margin-top: 4px;
}
</style>
