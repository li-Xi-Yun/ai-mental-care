<template>
  <el-dialog
    :model-value="modelValue"
    :title="`编辑配置：${resourceName}`"
    width="520px"
    align-center
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="handleClosed"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="config-form">
      <!-- 资源名称（只读） -->
      <el-form-item label="资源名称">
        <el-input :model-value="resourceName" disabled />
      </el-form-item>

      <!-- 默认配额 -->
      <el-form-item label="默认配额（次）" prop="defaultLimit">
        <el-input-number
          v-model="form.defaultLimit"
          :min="0"
          :step="1"
          :max="99999"
          controls-position="right"
          placeholder="请输入默认次数上限"
          style="width: 200px"
        />
        <div class="form-tip">所有用户未单独设置配额时使用的默认次数上限</div>
      </el-form-item>

      <!-- 资源可用 -->
      <el-form-item label="资源可用">
        <el-radio-group v-model="form.available">
          <el-radio :value="1">可用</el-radio>
          <el-radio :value="0">已停用</el-radio>
        </el-radio-group>
        <div v-if="form.available === 0" class="form-tip tip-warning">停用后，正在使用该资源的请求将被拒绝，请谨慎操作</div>
      </el-form-item>

      <!-- 启用限流 -->
      <el-form-item label="启用限流">
        <el-radio-group v-model="form.enabled">
          <el-radio :value="1">已启用</el-radio>
          <el-radio :value="0">未启用</el-radio>
        </el-radio-group>
        <div v-if="form.enabled === 0" class="form-tip tip-warning">关闭后不检查也不扣减配额，用户可无限使用该资源</div>
      </el-form-item>

      <!-- 计量周期 -->
      <el-form-item label="计量周期">
        <el-radio-group v-model="form.cycleType">
          <el-radio v-for="c in QUOTA_CYCLE_OPTIONS" :key="c.value" :value="c.value">{{ c.label }}</el-radio>
        </el-radio-group>
        <div v-if="form.cycleType === 1" class="form-tip">用户配额将在每月周期开始时重新计算</div>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取 消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">确 定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from "vue";
import type { FormInstance, FormRules } from "element-plus";
import { QUOTA_CYCLE_OPTIONS } from "@/admin/constants/quota";
import type { QuotaConfigForm } from "@/admin/views/quota/useQuotaList";

const props = withDefaults(
  defineProps<{
    modelValue: boolean;
    /** 资源名称（展示用，由父级经资源元数据解析） */
    resourceName?: string;
    submitting?: boolean;
    /** 表单数据由父级持有，子组件只负责渲染与校验 */
    form: QuotaConfigForm;
  }>(),
  { resourceName: "", submitting: false },
);

const emit = defineEmits<{
  (e: "update:modelValue", v: boolean): void;
  (e: "submit"): void;
}>();

const formRef = ref<FormInstance>();

const rules: FormRules = {
  defaultLimit: [
    {
      validator: (_rule, value: number | null, callback) => {
        if (props.form.enabled === 1) {
          if (value === null || value === undefined) {
            callback(new Error("启用限流时必须设置默认配额"));
            return;
          }
          if (value <= 0) {
            callback(new Error("启用限流时默认配额必须大于 0"));
            return;
          }
        } else if (value !== null && value !== undefined && value < 0) {
          callback(new Error("默认配额不能小于 0"));
          return;
        }
        callback();
      },
      trigger: ["change", "blur"],
    },
  ],
};

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  emit("submit");
}

function handleClosed() {
  formRef.value?.clearValidate();
}
</script>

<style scoped>
.config-form :deep(.el-form-item__label) {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}

.form-tip {
  margin-top: 6px;
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.6;
}

.tip-warning {
  color: #d97706;
}
</style>
