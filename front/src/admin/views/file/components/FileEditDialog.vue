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
      <el-form-item label="知识类型">
        <el-select v-model="form.knowledgeType" placeholder="请选择知识类型" style="width: 100%">
          <el-option
            v-for="opt in knowledgeTypeOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
            :disabled="opt.value === 0 && vectorEnabled"
          />
        </el-select>
        <div v-if="vectorEnabled" class="form-tip">
          该文件已启用向量，需先在向量管理页删除向量后才能改为「无」
        </div>
        <div v-else class="form-tip">知识类型非「无」时才可进行向量化</div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="emit('submit')">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from "vue";
import type { CategoryNode } from "../useFileList";
import { KNOWLEDGE_TYPE_MAP } from "@/admin/constants/file";

const props = defineProps<{
  modelValue: boolean;
  form: { originalName: string; categoryId?: number; knowledgeType: number };
  saving: boolean;
  /** 该文件是否已启用向量（启用时不允许把知识类型改为「无」） */
  vectorEnabled?: boolean;
  categoryTree: CategoryNode[];
  categoryTreeProps: { children: string; label: string };
}>();

const emit = defineEmits<{
  (e: "update:modelValue", v: boolean): void;
  (e: "submit"): void;
}>();

const vectorEnabled = computed(() => !!props.vectorEnabled);

const knowledgeTypeOptions = computed(() =>
  Object.entries(KNOWLEDGE_TYPE_MAP).map(([value, label]) => ({ value: Number(value), label })),
);
</script>

<style scoped>
.form-tip {
  margin-top: 6px;
  font-size: 12px;
  color: var(--admin-text-muted, #9ca3af);
  line-height: 1.6;
}
</style>
