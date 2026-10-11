<!-- 角色新增 / 编辑弹窗：内部自持表单状态，校验通过后向上回传提交载荷 -->
<template>
  <el-dialog
    :model-value="modelValue"
    :title="target?.id ? '编辑角色' : '新增角色'"
    width="440"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
    @closed="resetForm"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
      <el-form-item label="角色名称" prop="name">
        <el-input v-model="form.name" placeholder="如 心理咨询师" maxlength="20" />
      </el-form-item>
      <el-form-item label="角色标识" prop="roleKey">
        <el-input v-model="form.roleKey" placeholder="如 counselor" :disabled="!!form.id" />
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="选填" maxlength="200" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from "vue";
import type { FormInstance, FormRules } from "element-plus";
import type { RoleItem, RoleSubmitPayload } from "../useRoleList";

const props = defineProps<{
  modelValue: boolean;
  target?: RoleItem | null;
  submitting?: boolean;
}>();
const emit = defineEmits<{
  (e: "update:modelValue", v: boolean): void;
  (e: "submit", payload: RoleSubmitPayload): void;
}>();

const formRef = ref<FormInstance>();
const form = reactive({ id: 0, name: "", roleKey: "", remark: "" });

const rules: FormRules = {
  name: [
    { required: true, message: "请输入角色名称", trigger: "blur" },
    { min: 2, max: 20, message: "长度 2-20", trigger: "blur" },
  ],
  roleKey: [{ required: true, message: "请输入角色标识", trigger: "blur" }],
};

// 弹窗打开时按目标行回填（等价于拆分前的 openDialog）
watch(
  () => props.modelValue,
  (visible) => {
    if (visible) fillForm();
  },
);

function fillForm() {
  form.id = props.target?.id ?? 0;
  form.name = props.target?.name ?? "";
  form.roleKey = props.target?.roleKey ?? "";
  form.remark = props.target?.remark ?? "";
}

function resetForm() {
  form.id = 0;
  form.name = "";
  form.roleKey = "";
  form.remark = "";
  formRef.value?.clearValidate();
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  emit("submit", { name: form.name, roleKey: form.roleKey, remark: form.remark });
}
</script>
