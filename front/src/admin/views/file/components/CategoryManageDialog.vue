<template>
  <el-dialog
    :model-value="modelValue"
    title="分类管理"
    width="460"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="category-manage-header">
      <span class="category-manage-tip">共 {{ categoryTree.length }} 个一级分类，支持多级嵌套</span>
      <el-button type="primary" size="small" @click="emit('add')">
        <el-icon style="margin-right: 3px"><Plus /></el-icon>
        新增分类
      </el-button>
    </div>
    <div class="category-manage-body">
      <el-tree
        :data="categoryTree"
        :props="categoryTreeProps"
        node-key="id"
        default-expand-all
        :expand-on-click-node="false"
      >
        <template #default="{ data }">
          <div class="category-manage-node">
            <el-icon :size="14"><Folder /></el-icon>
            <span class="node-name">{{ data.categoryName }}</span>
            <span v-if="data.defaultType === 1" class="node-default">默认</span>
            <span v-if="data.fileCount" class="node-count">{{ data.fileCount }}</span>
            <span class="cm-actions">
              <el-button text size="small" type="primary" @click.stop="emit('edit', data)">编辑</el-button>
              <el-button text size="small" type="danger" @click.stop="emit('remove', data)">删除</el-button>
            </span>
          </div>
        </template>
      </el-tree>
    </div>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { Folder, Plus } from "@element-plus/icons-vue";
import type { CategoryNode } from "../useFileList";

defineProps<{
  modelValue: boolean;
  categoryTree: CategoryNode[];
  categoryTreeProps: { children: string; label: string };
}>();

const emit = defineEmits<{
  (e: "update:modelValue", v: boolean): void;
  (e: "add"): void;
  (e: "edit", category: CategoryNode): void;
  (e: "remove", category: CategoryNode): void;
}>();
</script>

<style scoped>
.category-manage-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.category-manage-tip {
  font-size: 12px;
  color: #94a3b8;
}

.category-manage-body {
  max-height: 380px;
  overflow-y: auto;
}

.category-manage-body :deep(.el-tree-node__content) {
  height: 36px;
  border-radius: 8px;
}

.category-manage-body :deep(.el-tree-node__content:hover) {
  background: #f8fafc;
}

.category-manage-node {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 5px;
  min-width: 0;
  padding-right: 4px;
}

.cm-actions {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
}

.cm-actions .el-button {
  margin-left: 4px;
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

.node-default {
  font-size: 10px;
  background: #eef2ff;
  color: #6366f1;
  border-radius: 4px;
  padding: 0 4px;
  line-height: 1.6;
  flex-shrink: 0;
}
</style>
