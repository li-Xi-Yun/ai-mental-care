<template>
  <el-dialog
    :model-value="modelValue"
    title="编辑白名单中文含义"
    width="560px"
    align-center
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="handleClosed"
  >
    <div class="label-editor">
      <div class="editor-tip">
        key（资产 id）上传后固定不可增删；value 为中文含义，可填写或清空。仅展示各模型白名单内资产。
      </div>
      <el-row :gutter="12">
        <el-col :span="12">
          <div class="group-title">
            表情（expression）
            <span class="group-count">{{ expressionKeys.length }}</span>
          </div>
          <div v-if="expressionKeys.length" class="row-list">
            <div v-for="key in expressionKeys" :key="key" class="label-row">
              <span class="row-key" :title="key">{{ key }}</span>
              <el-input
                :model-value="localExpression[key] ?? ''"
                size="small"
                placeholder="中文含义（可空）"
                clearable
                @update:model-value="(v) => setLabel('expression', key, String(v ?? ''))"
              />
            </div>
          </div>
          <el-empty v-else :image-size="40" description="无表情资产" />
        </el-col>
        <el-col :span="12">
          <div class="group-title">
            动作（motion）
            <span class="group-count">{{ motionKeys.length }}</span>
          </div>
          <div v-if="motionKeys.length" class="row-list">
            <div v-for="key in motionKeys" :key="key" class="label-row">
              <span class="row-key" :title="key">{{ key }}</span>
              <el-input
                :model-value="localMotion[key] ?? ''"
                size="small"
                placeholder="中文含义（可空）"
                clearable
                @update:model-value="(v) => setLabel('motion', key, String(v ?? ''))"
              />
            </div>
          </div>
          <el-empty v-else :image-size="40" description="无动作资产" />
        </el-col>
      </el-row>
    </div>

    <template #footer>
      <div class="editor-footer">
        <el-button size="small" :disabled="!importable" @click="handleImport">
          从语义提示词导入
        </el-button>
        <span class="footer-spacer"></span>
        <el-button :disabled="submitting" @click="emit('update:modelValue', false)">取 消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSave">保 存</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive } from "vue";
import { parseLabelMap } from "@/admin/utils/live2dLabel";

/**
 * 白名单 key（只读）+ value（中文含义）行式编辑器。
 * key 来源：detail 的 expressionList/motionList 的 keySet（白名单固定）；
 * value 初始值：detail 中已保存的中文。
 */
const props = defineProps<{
  modelValue: boolean;
  submitting?: boolean;
  /** 语义提示词（供「从提示词导入」预填 value） */
  semanticPrompt?: string;
  /** id → 中文 现有值（key 即白名单） */
  expressionList?: Record<string, string>;
  motionList?: Record<string, string>;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", v: boolean): void;
  (e: "save", payload: { expressionList: Record<string, string>; motionList: Record<string, string> }): void;
}>();

const expressionKeys = computed(() => Object.keys(props.expressionList ?? {}));
const motionKeys = computed(() => Object.keys(props.motionList ?? {}));
const importable = computed(() => !!props.semanticPrompt?.trim() && (expressionKeys.value.length || motionKeys.value.length));

const localExpression = reactive<Record<string, string>>(Object.fromEntries(expressionKeys.value.map((k) => [k, props.expressionList?.[k] ?? ""])));
const localMotion = reactive<Record<string, string>>(Object.fromEntries(motionKeys.value.map((k) => [k, props.motionList?.[k] ?? ""])));

// 每次打开时重置本地编辑态到 props 当前值
let initialized = false;
function ensureInitialized() {
  if (props.modelValue && !initialized) {
    for (const k of expressionKeys.value) localExpression[k] = props.expressionList?.[k] ?? "";
    for (const k of motionKeys.value) localMotion[k] = props.motionList?.[k] ?? "";
    initialized = true;
  }
}

function setLabel(kind: "expression" | "motion", key: string, value: string) {
  const target = kind === "expression" ? localExpression : localMotion;
  target[key] = value;
}

/** 从语义提示词解析「中文=id」→ 按白名单 key 回填 value（仅覆盖白名单内的 key） */
function handleImport() {
  const parsedExpr = parseLabelMap(props.semanticPrompt ?? "", expressionKeys.value);
  Object.entries(parsedExpr).forEach(([k, v]) => {
    if (localExpression[k] !== undefined) localExpression[k] = v;
  });
  const parsedMotion = parseLabelMap(props.semanticPrompt ?? "", motionKeys.value);
  Object.entries(parsedMotion).forEach(([k, v]) => {
    if (localMotion[k] !== undefined) localMotion[k] = v;
  });
}

function buildResult() {
  const expressionList: Record<string, string> = {};
  for (const k of expressionKeys.value) expressionList[k] = localExpression[k] ?? "";
  const motionList: Record<string, string> = {};
  for (const k of motionKeys.value) motionList[k] = localMotion[k] ?? "";
  return { expressionList, motionList };
}

function handleSave() {
  emit("save", buildResult());
}

function handleClosed() {
  initialized = false;
}

// 打开时初始化一次（init 需在模板渲染前，确保 props 就绪）
void (() => ensureInitialized())();
</script>

<style scoped>
.label-editor {
  max-height: 52vh;
  overflow-y: auto;
}

.editor-tip {
  font-size: 12px;
  color: #94a3b8;
  margin-bottom: 12px;
  line-height: 1.6;
}

.group-title {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.group-count {
  font-size: 11px;
  font-weight: 500;
  color: #94a3b8;
  background: #f1f5f9;
  border-radius: 999px;
  padding: 0 7px;
}

.row-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.label-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.row-key {
  flex-shrink: 0;
  min-width: 84px;
  font-family: ui-monospace, "SFMono-Regular", Consolas, monospace;
  font-size: 12px;
  color: #64748b;
  background: #f8fafc;
  border-radius: 4px;
  padding: 1px 6px;
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.editor-footer {
  display: flex;
  align-items: center;
  width: 100%;
}

.footer-spacer {
  flex: 1;
}
</style>