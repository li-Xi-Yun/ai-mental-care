<template>
  <el-dialog
    :model-value="modelValue"
    :title="dialogTitle"
    width="560px"
    align-center
    :close-on-click-modal="true"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="handleClosed"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="adjust-form">
      <!-- 配额模式 -->
      <el-form-item label="配额模式" prop="mode">
        <el-radio-group v-model="form.mode">
          <el-radio :value="0">跟随全局配置</el-radio>
          <el-radio :value="1">设置自定义上限</el-radio>
          <el-radio :value="2">不限额</el-radio>
        </el-radio-group>
        <div class="form-tip">{{ modeTip }}</div>
      </el-form-item>

      <!-- 自定义上限 -->
      <el-form-item v-if="form.mode === 1" label="自定义上限（次）" prop="customLimit">
        <el-input-number
          v-model="form.customLimit"
          :min="0"
          :step="1"
          controls-position="right"
          placeholder="请输入次数上限"
          style="width: 220px"
        />
        <div v-if="form.mode === 1 && usedCount != null" class="form-tip" :class="{ 'tip-danger': form.customLimit != null && form.customLimit < usedCount }">
          <template v-if="form.customLimit != null && form.customLimit < usedCount">
            已用 {{ usedCount }} 次，低于该上限将立即阻断使用，请谨慎设置
          </template>
          <template v-else>
            当前已用 {{ usedCount }} 次
          </template>
        </div>
      </el-form-item>

      <!-- 不限额二次确认提示 -->
      <el-form-item v-if="form.mode === 2" label="">
        <div class="unlimited-tip">
          <el-icon class="tip-icon"><WarningFilled /></el-icon>
          不限额用户的使用不会计入配额消费记录
        </div>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取 消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">确 定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import type { FormInstance, FormRules } from "element-plus";
import { QUOTA_RESOURCE_META, quotaResourceName } from "@/admin/constants/quota";
import type { QuotaUserVO, QuotaUserAdjustDTO } from "@/admin/api/quota/quota";

const props = withDefaults(
  defineProps<{
    modelValue: boolean;
    /** 用户ID（雪花ID，字符串形态） */
    userId?: number | string | null;
    resourceType?: number | null;
    /** 当前资源配额行 */
    row?: QuotaUserVO | null;
    submitting?: boolean;
  }>(),
  { userId: null, resourceType: null, row: null, submitting: false },
);

const emit = defineEmits<{
  (e: "update:modelValue", v: boolean): void;
  /** 校验通过后回传调整载荷，提交与刷新由父级负责 */
  (e: "submit", payload: QuotaUserAdjustDTO): void;
}>();

const formRef = ref<FormInstance>();

/** 配额模式：0=跟随全局，1=设置自定义上限，2=不限额 */
const form = reactive<{ mode: 0 | 1 | 2; customLimit: number | null }>({ mode: 0, customLimit: null });

const usedCount = computed(() => (props.row?.usedCount != null ? props.row.usedCount : null));

const dialogTitle = computed(() => {
  const name = props.resourceType == null ? "" : (QUOTA_RESOURCE_META[props.resourceType]?.name ?? quotaResourceName(props.resourceType));
  return `调整配额：${name || "未知资源"}`;
});

const modeTip = computed(() => {
  if (props.row == null) return "";
  switch (form.mode) {
    case 0:
      return "跟随全局默认上限，不单独设置个性化上限";
    case 1:
      return "为当前用户单独设置次数上限，覆盖全局默认配置";
    case 2:
      return "该用户使用此资源不限额、不计数、不写消费记录";
  }
});

const rules: FormRules = {
  customLimit: [
    {
      validator: (_rule, value: number | null, callback) => {
        if (form.mode !== 1) {
          callback();
          return;
        }
        if (value === null || value === undefined) {
          callback(new Error("请输入自定义上限"));
          return;
        }
        if (value < 0) {
          callback(new Error("自定义上限不能小于 0"));
          return;
        }
        if (usedCount.value != null && value < usedCount.value) {
          callback(new Error(`已用次数超过该上限（已用 ${usedCount.value} 次）`));
          return;
        }
        callback();
      },
      trigger: ["change", "blur"],
    },
  ],
};

/** 弹窗打开时按当前配额行回填模式 */
function resetForm() {
  const r = props.row;
  if (r && r.unlimited === 1) {
    form.mode = 2;
    form.customLimit = null;
  } else if (r && r.customLimit !== null && r.customLimit !== undefined) {
    form.mode = 1;
    form.customLimit = r.customLimit;
  } else {
    form.mode = 0;
    form.customLimit = null;
  }
}

function handleClosed() {
  formRef.value?.clearValidate();
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;

  // 切到不限额：二次确认
  if (form.mode === 2) {
    const { ElMessageBox } = await import("element-plus");
    try {
      await ElMessageBox.confirm("不限额用户的使用不会计入配额消费记录，确定设置该用户为不限额吗？", "不限额确认", {
        type: "warning",
        confirmButtonText: "确认设置",
        cancelButtonText: "取消",
      });
    } catch {
      return;
    }
  }

  const r = props.row;
  const payload: QuotaUserAdjustDTO = {
    resourceType: r?.resourceType ?? props.resourceType ?? 0,
    customLimit: form.mode === 1 ? form.customLimit : null,
    unlimited: form.mode === 2 ? 1 : 0,
    used: r?.usedCount ?? 0,
  };
  emit("submit", payload);
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) resetForm();
  },
);
</script>

<style scoped>
.adjust-form :deep(.el-form-item__label) {
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

.tip-danger {
  color: #ef4444;
}

.unlimited-tip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border-radius: 8px;
  background: #fffbeb;
  border: 1px solid #fde68a;
  font-size: 12px;
  color: #d97706;
}

.tip-icon {
  color: #f59e0b;
}
</style>