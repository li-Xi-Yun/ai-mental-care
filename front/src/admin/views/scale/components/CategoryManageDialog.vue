<template>
  <el-dialog
    :model-value="modelValue"
    title="量表分类管理"
    width="620px"
    align-center
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="category-toolbar">
      <span class="category-hint">共 {{ categories.length }} 个分类，支持新增、编辑、排序与删除</span>
      <div class="toolbar-spacer" />
      <el-button type="primary" size="small" :loading="submitting" @click="emit('add-category')">
        <el-icon class="btn-icon"><Plus /></el-icon>新增分类
      </el-button>
    </div>

    <el-table v-loading="!!loading" :data="categories" class="category-table" row-key="id">
      <el-table-column label="排序" width="120" align="center">
        <template #default="{ row }">
          <div class="sort-actions">
            <el-button
              text
              size="small"
              class="sort-btn"
              :disabled="row.id === categories[0]?.id"
              @click="emit('move-category', row as ScaleCategoryRow, -1)"
            >
              <el-icon><CaretTop /></el-icon>
            </el-button>
            <span class="sort-num">{{ row.sort ?? "·" }}</span>
            <el-button
              text
              size="small"
              class="sort-btn"
              :disabled="row.id === categories[categories.length - 1]?.id"
              @click="emit('move-category', row as ScaleCategoryRow, 1)"
            >
              <el-icon><CaretBottom /></el-icon>
            </el-button>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="categoryName" label="分类名称" min-width="170" />
      <el-table-column label="量表数" width="90" align="center">
        <template #default="{ row }">
          <span class="num-text">{{ row.scaleCount ?? 0 }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="108" align="center">
        <template #default="{ row }">
          <div class="op-cell">
            <el-button text size="small" class="op-btn op-edit" @click="emit('edit-category', row as ScaleCategoryRow)">编辑</el-button>
            <el-button text size="small" class="op-btn op-del" @click="emit('delete-category', row as ScaleCategoryRow)">删除</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关 闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { CaretBottom, CaretTop, Plus } from "@element-plus/icons-vue";
import type { ScaleCategoryRow } from "@/admin/views/scale/useScaleList";

defineProps<{
  modelValue: boolean;
  categories: ScaleCategoryRow[];
  loading?: boolean;
  submitting?: boolean;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", v: boolean): void;
  (e: "add-category"): void;
  (e: "edit-category", row: ScaleCategoryRow): void;
  (e: "move-category", row: ScaleCategoryRow, dir: -1 | 1): void;
  (e: "delete-category", row: ScaleCategoryRow): void;
}>();
</script>

<style scoped>
/* ==================== 分类管理 ==================== */

.category-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.category-hint {
  font-size: 12px;
  color: #94a3b8;
}

.category-table {
  --el-table-header-bg-color: #f8fafc;
  --el-table-header-text-color: #475569;
  --el-table-border-color: #eef1f6;
  --el-table-row-hover-bg-color: #f8faff;
  width: 100%;
  border-radius: 10px;
  overflow: hidden;
}

.sort-actions {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.sort-btn {
  font-size: 14px;
  color: #94a3b8;
}

.sort-btn:not(.is-disabled):hover {
  color: #6366f1;
}

.sort-num {
  min-width: 20px;
  font-size: 12px;
  font-weight: 600;
  color: #475569;
  font-variant-numeric: tabular-nums;
}

.category-table :deep(.el-table__cell) {
  padding: 10px 0;
}

.num-text {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  font-variant-numeric: tabular-nums;
}

/* 弹窗内容可能挂载到 body，保留作用域副本确保弹窗内样式一致 */
.toolbar-spacer {
  flex: 1;
}

.btn-icon {
  margin-right: 4px;
}
</style>
