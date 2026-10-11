<template>
  <el-dialog
    :model-value="modelValue"
    :title="isEditing ? '编辑分类' : '新增分类'"
    width="380"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form label-width="70px">
      <el-form-item v-if="!isEditing" label="父分类">
        <el-tree-select
          :model-value="parentId"
          :data="categoryTree"
          :props="categoryTreeProps"
          node-key="id"
          check-strictly
          clearable
          placeholder="不选则为一级分类"
          style="width: 100%"
          :default-expanded-keys="[]"
          @update:model-value="emit('update:parentId', $event)"
        />
      </el-form-item>
      <el-form-item label="分类名称" required>
        <el-input
          :model-value="name"
          placeholder="请输入分类名称"
          maxlength="20"
          show-word-limit
          @update:model-value="emit('update:name', $event)"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="emit('submit')">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import type { CategoryNode } from "../useFileList";

defineProps<{
  modelValue: boolean;
  /** 编辑态（true）或新增态（false） */
  isEditing: boolean;
  name: string;
  parentId?: number;
  saving: boolean;
  categoryTree: CategoryNode[];
  categoryTreeProps: { children: string; label: string };
}>();

const emit = defineEmits<{
  (e: "update:modelValue", v: boolean): void;
  (e: "update:name", v: string): void;
  (e: "update:parentId", v: number | undefined): void;
  (e: "submit"): void;
}>();
</script>
