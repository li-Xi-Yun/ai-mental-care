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
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="symptom-form">
      <el-form-item label="标准症状术语" prop="symptomTerm">
        <el-input
          v-model="form.symptomTerm"
          placeholder="例：持续情绪低落"
          maxlength="50"
          show-word-limit
          clearable
        />
      </el-form-item>

      <el-form-item label="症状大类" prop="symptomCategory">
        <el-select v-model="form.symptomCategory" placeholder="请选择症状大类" style="width: 100%">
          <el-option v-for="c in categoryOptions" :key="c" :label="c" :value="c" />
        </el-select>
        <div class="form-tip">情绪症状 / 躯体症状 / 认知症状 / 行为症状</div>
      </el-form-item>

      <el-form-item label="同义口语词" prop="synonymWords">
        <div class="synonym-editor">
          <el-tag
            v-for="(w, i) in form.synonymWords"
            :key="i"
            class="syn-chip"
            closable
            @close="removeSynonym(i)"
          >
            {{ w }}
          </el-tag>
          <el-input
            ref="synonymInputRef"
            v-model="synonymInput"
            size="small"
            class="synonym-input"
            placeholder="输入后回车添加"
            :maxlength="20"
            @keyup.enter="addSynonym"
            @blur="addSynonym"
          />
        </div>
        <div class="form-tip">用于 AI 识别用户口语化表达，如「睡不着」「翻来覆去」</div>
      </el-form-item>

      <el-form-item label="默认严重等级" prop="severityDefault">
        <div class="sev-picker">
          <span
            v-for="n in SYMPTOM_MAX_SEVERITY"
            :key="n"
            class="sev-seg"
            :class="{ on: n <= form.severityDefault }"
            :style="n <= form.severityDefault ? levelSegStyle(n) : {}"
            @click="form.severityDefault = n"
          >
            {{ n }}
          </span>
          <span class="sev-meta" :style="{ color: levelColor(form.severityDefault) }">
            {{ severityText(form.severityDefault) }}
          </span>
        </div>
      </el-form-item>

      <el-form-item label="状态">
        <el-radio-group v-model="form.status">
          <el-radio-button :value="1">启用</el-radio-button>
          <el-radio-button :value="0">禁用</el-radio-button>
        </el-radio-group>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取 消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">确 定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from "vue";
import { ElInput, ElMessage } from "element-plus";
import type { FormInstance, FormRules } from "element-plus";
import { SYMPTOM_MAX_SEVERITY } from "@/shared/api/config";
import { levelColor, levelSegStyle, severityText } from "@/admin/constants/symptom";
import type { SymptomRow, SymptomSubmitPayload } from "../useSymptomList";

/**
 * 症状新增 / 编辑弹窗：自持表单状态与校验，
 * 校验通过后通过 submit 事件回传载荷，由父组件调用接口。
 */
const props = defineProps<{
  modelValue: boolean;
  title: string;
  submitting: boolean;
  /** 编辑目标行；为 null 表示新增 */
  target: SymptomRow | null;
  categoryOptions: string[];
}>();

const emit = defineEmits<{
  (e: "update:modelValue", v: boolean): void;
  (e: "submit", payload: SymptomSubmitPayload): void;
}>();

interface SymptomForm {
  id: number | null;
  symptomTerm: string;
  symptomCategory: string;
  synonymWords: string[];
  severityDefault: number;
  status: number;
}

const formRef = ref<FormInstance>();
const synonymInputRef = ref<InstanceType<typeof ElInput>>();
const synonymInput = ref("");

const emptyForm: SymptomForm = {
  id: null,
  symptomTerm: "",
  symptomCategory: "",
  synonymWords: [],
  severityDefault: 1,
  status: 1,
};

const form = reactive<SymptomForm>({ ...emptyForm });

const rules: FormRules = {
  symptomTerm: [{ required: true, message: "请输入标准症状术语", trigger: "blur" }],
  symptomCategory: [{ required: true, message: "请选择症状大类", trigger: "change" }],
  severityDefault: [{ required: true, message: "请选择默认严重等级", trigger: "change" }],
};

/** 打开弹窗时按目标行初始化表单（target 为 null 则重置为新增默认值） */
function applyTarget(target: SymptomRow | null) {
  if (!target) {
    Object.assign(form, emptyForm);
    return;
  }
  Object.assign(form, {
    id: target.id,
    symptomTerm: target.symptomTerm,
    symptomCategory: target.symptomCategory,
    synonymWords: [...(target.synonymWords ?? [])],
    severityDefault: target.severityDefault ?? 1,
    status: target.status ?? 1,
  });
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) applyTarget(props.target);
  },
);

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  emit("submit", {
    id: form.id,
    symptomTerm: form.symptomTerm.trim(),
    symptomCategory: form.symptomCategory,
    synonymWords: form.synonymWords,
    severityDefault: form.severityDefault,
    status: form.status,
  });
}

function handleClosed() {
  formRef.value?.clearValidate();
  synonymInput.value = "";
}

function addSynonym() {
  const word = synonymInput.value.trim();
  synonymInput.value = "";
  if (!word) return;
  if (form.synonymWords.includes(word)) {
    ElMessage.warning("同义词已存在，请勿重复添加");
    return;
  }
  form.synonymWords.push(word);
}

function removeSynonym(index: number) {
  form.synonymWords.splice(index, 1);
}
</script>

<style scoped>
.symptom-form :deep(.el-form-item__label) {
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

/* 同义词编辑区 */
.synonym-editor {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  width: 100%;
  min-height: 42px;
  padding: 8px 10px;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  background: #f8fafc;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.synonym-editor:focus-within {
  border-color: #6366f1;
  box-shadow: 0 0 0 2px rgba(99, 102, 241, 0.12);
  background: #fff;
}

.synonym-editor :deep(.el-tag) {
  border: none;
}

.syn-chip {
  display: inline-block;
  padding: 2px 9px;
  border-radius: 999px;
  background: #eef2ff;
  color: #4f46e5;
  font-size: 11.5px;
  border: 1px solid #e0e7ff;
  line-height: 1.5;
}

.synonym-input {
  width: 180px;
  flex-shrink: 1;
}

.synonym-input :deep(.el-input__wrapper) {
  background: transparent;
  box-shadow: none;
  padding: 0 4px;
}

/* 严重等级选择器 */
.sev-picker {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.sev-seg {
  width: 40px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  color: #94a3b8;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  cursor: pointer;
  user-select: none;
  transition: all 0.15s ease;
}

.sev-seg:hover {
  border-color: #6366f1;
  color: #6366f1;
}

.sev-seg.on {
  border-color: transparent;
}

.sev-meta {
  margin-left: 4px;
  font-size: 13px;
  font-weight: 600;
  min-width: 34px;
}
</style>
