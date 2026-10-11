<template>
  <el-dialog
    :model-value="modelValue"
    :title="`封禁账号：${target?.username ?? ''}`"
    width="480px"
    align-center
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form label-position="top">
      <el-form-item label="封禁截止时间">
        <el-date-picker
          v-model="form.banEndTime"
          type="datetime"
          placeholder="留空表示永久封禁"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 100%"
          :disabled-date="disablePastDate"
        />
      </el-form-item>
      <el-form-item label="封禁理由">
        <el-input v-model="form.banReason" type="textarea" :rows="3" placeholder="请输入封禁理由" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取 消</el-button>
      <el-button type="danger" :loading="submitting" @click="handleSubmit">确认封禁</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, watch } from "vue";
import type { AdminPageVO } from "@/admin/api/admin/admin-manage";
import type { AdminBanSubmitPayload } from "../useAdminList";

/**
 * 封禁管理员弹窗：
 * 表单状态内部自持，打开时重置，点击确认后 emit('submit', payload)。
 */
const props = defineProps<{
  modelValue: boolean;
  target?: AdminPageVO | null;
  submitting?: boolean;
}>();
const emit = defineEmits<{
  (e: "update:modelValue", v: boolean): void;
  (e: "submit", payload: AdminBanSubmitPayload): void;
}>();

const form = reactive({ banEndTime: "", banReason: "" });

function disablePastDate(d: Date): boolean {
  return d.getTime() < Date.now();
}

// 打开弹窗时重置表单（与拆分前 handleStatusToggle 中的重置行为一致）
watch(
  () => props.modelValue,
  (v) => {
    if (v) {
      form.banEndTime = "";
      form.banReason = "";
    }
  },
);

function handleSubmit() {
  emit("submit", { banReason: form.banReason, banEndTime: form.banEndTime || undefined });
}
</script>
