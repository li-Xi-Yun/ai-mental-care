<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="560px"
    align-center
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="handleClosed"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="node-form">
      <el-form-item label="节点标识 nodeKey" prop="nodeKey">
        <el-input
          v-model="form.nodeKey"
          placeholder="如 psychologicalState"
          maxlength="50"
          clearable
          :disabled="isEdit"
        />
        <div class="form-tip">新增后不可修改，仅支持字母 / 数字 / 下划线</div>
      </el-form-item>

      <el-form-item label="节点名称" prop="nodeName">
        <el-input v-model="form.nodeName" placeholder="如 心理状态与症状评估" maxlength="50" clearable />
      </el-form-item>

      <el-form-item label="节点分组" prop="nodeGroup">
        <el-select v-model="form.nodeGroup" placeholder="选择已存在的分组，或输入新分组" filterable allow-create default-first-option clearable style="width: 100%">
          <el-option v-for="g in groupOptions" :key="g.nodeGroup" :label="g.nodeGroup" :value="g.nodeGroup ?? ''" />
        </el-select>
        <div class="form-tip">例如 process（主流程）/ input（输入）/ knowledge（知识）</div>
      </el-form-item>

      <el-form-item label="模型类型" prop="modelType">
        <el-select v-model="form.modelType" placeholder="请选择模型类型" style="width: 100%">
          <el-option v-for="m in AI_NODE_MODEL_TYPE_OPTIONS" :key="m.value" :label="m.label" :value="m.value" />
        </el-select>
      </el-form-item>

      <el-form-item label="启用状态">
        <el-radio-group v-model="form.enabled">
          <el-radio-button :value="1">启用</el-radio-button>
          <el-radio-button :value="0">禁用</el-radio-button>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="系统提示词" prop="systemPrompt">
        <el-input
          v-model="form.systemPrompt"
          type="textarea"
          :rows="3"
          maxlength="20000"
          show-word-limit
          placeholder="填写节点运行时的角色与任务约定……"
        />
      </el-form-item>

      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="节点用途备注" maxlength="255" show-word-limit />
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
import type { AiNodeGroupVO } from "@/admin/api/ai-node/config";
import { AI_NODE_MODEL_TYPE_OPTIONS } from "@/admin/constants/aiNode";
import type { NodeForm } from "@/admin/views/ai-node/useAiNodeList";

defineProps<{
  modelValue: boolean;
  title?: string;
  /** 编辑模式：nodeKey 不可修改 */
  isEdit?: boolean;
  submitting?: boolean;
  /** 表单数据由 useAiNodeList 持有，子组件只负责渲染与校验 */
  form: NodeForm;
  groupOptions: AiNodeGroupVO[];
}>();
const emit = defineEmits<{
  (e: "update:modelValue", v: boolean): void;
  (e: "submit"): void;
}>();

const formRef = ref<FormInstance>();

const rules: FormRules = {
  nodeKey: [
    { required: true, message: "请输入节点标识", trigger: "blur" },
    { pattern: /^[A-Za-z0-9_]+$/, message: "仅支持字母 / 数字 / 下划线", trigger: "blur" },
  ],
  nodeName: [{ required: true, message: "请输入节点名称", trigger: "blur" }],
  nodeGroup: [{ required: true, message: "请选择或输入节点分组", trigger: "change" }],
  modelType: [{ required: true, message: "请选择模型类型", trigger: "change" }],
  systemPrompt: [{ required: true, message: "请输入系统提示词", trigger: "blur" }],
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
.node-form :deep(.el-form-item__label) {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  margin-bottom: 2px;
}

.form-tip {
  margin-top: 6px;
  font-size: 11.5px;
  color: #94a3b8;
  line-height: 1.5;
}
</style>
