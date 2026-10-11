<template>
  <el-dialog
    :model-value="modelValue"
    :title="`编辑角色：${target?.username ?? ''}`"
    width="480px"
    align-center
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form label-position="top">
      <el-form-item label="角色">
        <el-select
          :model-value="selectedRoleIds"
          multiple
          filterable
          collapse-tags
          collapse-tags-tooltip
          placeholder="请选择角色"
          style="width: 100%"
          :loading="loading"
          @update:model-value="val => emit('update:selectedRoleIds', val)"
        >
          <el-option v-for="r in roleOptions" :key="r.id" :label="r.name" :value="r.id" />
        </el-select>
        <div class="form-tip">保存后将覆盖该用户原有的全部角色</div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取 消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">确 定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import type { SimpleRole } from "@/admin/api/admin/user-manage";

defineProps<{
  modelValue: boolean;
  target?: { username?: string } | null;
  loading?: boolean;
  submitting?: boolean;
  roleOptions: SimpleRole[];
  selectedRoleIds: number[];
}>();
const emit = defineEmits<{
  (e: "update:modelValue", v: boolean): void;
  (e: "update:selectedRoleIds", v: number[]): void;
  (e: "submit"): void;
}>();

function handleSubmit() {
  emit("submit");
}
</script>

<style scoped>
.form-tip {
  margin-top: 6px;
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.6;
}
</style>