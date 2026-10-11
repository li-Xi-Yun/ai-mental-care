<!-- 权限新增 / 编辑弹窗：内部自持表单状态，校验通过后向上回传提交载荷 -->
<template>
  <el-dialog
    :model-value="modelValue"
    :title="target ? '编辑权限' : '新增权限'"
    width="420"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
    @closed="resetForm"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
      <el-form-item label="权限标识" prop="perms">
        <el-input v-model="form.perms" placeholder="如 sys:permission:add" :disabled="!!form.id" />
      </el-form-item>
      <el-form-item label="权限名称" prop="name">
        <el-input v-model="form.name" placeholder="请输入权限名称" maxlength="20" />
      </el-form-item>
      <el-form-item label="分组名称" prop="groupName">
        <el-select v-model="form.groupName" placeholder="选择或输入分组" filterable allow-create style="width: 100%">
          <el-option v-for="g in groupOptions" :key="g" :label="g" :value="g" />
        </el-select>
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
import type { PermissionItem, PermissionSubmitPayload } from "../usePermissionList";

const props = defineProps<{
  modelValue: boolean;
  target?: PermissionItem | null;
  groupName?: string;
  detailRemark?: string;
  groupOptions: string[];
  submitting?: boolean;
}>();
const emit = defineEmits<{
  (e: "update:modelValue", v: boolean): void;
  (e: "submit", payload: PermissionSubmitPayload): void;
}>();

const formRef = ref<FormInstance>();
const form = reactive({ id: 0, perms: "", name: "", groupName: "", remark: "" });

const rules: FormRules = {
  perms: [
    { required: true, message: "请输入权限标识", trigger: "blur" },
    { min: 2, max: 50, message: "长度 2-50", trigger: "blur" },
  ],
  name: [
    { required: true, message: "请输入权限名称", trigger: "blur" },
    { min: 2, max: 20, message: "长度 2-20", trigger: "blur" },
  ],
  groupName: [{ required: true, message: "请输入分组名称", trigger: "change" }],
};

// 弹窗打开时按目标行回填（等价于拆分前的 openDialog）
watch(
  () => props.modelValue,
  (visible) => {
    if (visible) fillForm();
  },
);

// 编辑时异步加载的备注回填（与拆分前 form.remark 的异步赋值一致）
watch(
  () => props.detailRemark,
  (v) => {
    form.remark = v ?? "";
  },
);

function fillForm() {
  form.id = props.target?.id ?? 0;
  form.perms = props.target?.perms ?? "";
  form.name = props.target?.name ?? "";
  form.groupName = props.groupName ?? "";
  form.remark = props.detailRemark ?? "";
}

function resetForm() {
  form.id = 0;
  form.perms = "";
  form.name = "";
  form.groupName = "";
  form.remark = "";
  formRef.value?.clearValidate();
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  emit("submit", { perms: form.perms, name: form.name, groupName: form.groupName, remark: form.remark });
}
</script>
