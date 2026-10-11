<template>
  <el-dialog
    :model-value="modelValue"
    title="编辑文件信息"
    width="440"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form label-width="80px">
      <el-form-item label="文件名">
        <el-input v-model="form.originalName" placeholder="请输入文件名（含后缀）" />
      </el-form-item>
      <el-form-item label="分类">
        <el-tree-select
          v-model="form.categoryId"
          :data="categoryTree"
          :props="categoryTreeProps"
          node-key="id"
          check-strictly
          clearable
          placeholder="请选择分类"
          style="width: 100%"
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
  form: { originalName: string; categoryId?: number };
  saving: boolean;
  categoryTree: CategoryNode[];
  categoryTreeProps: { children: string; label: string };
}>();

const emit = defineEmits<{
  (e: "update:modelValue", v: boolean): void;
  (e: "submit"): void;
}>();
</script>
