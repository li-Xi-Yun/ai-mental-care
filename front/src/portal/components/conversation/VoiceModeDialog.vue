<template>
  <el-dialog
    v-model="visibleProxy"
    :title="title"
    width="460px"
    align-center
    :close-on-click-modal="false"
    class="voice-mode-dialog"
  >
    <div class="vm-block">
      <div class="vm-label">输入方式（可多选）</div>
      <el-checkbox-group v-model="inputTypes" class="vm-group">
        <el-checkbox-button v-for="opt in VOICE_INPUT_OPTIONS" :key="opt.value" :value="opt.value">
          {{ opt.label }}
        </el-checkbox-button>
      </el-checkbox-group>
      <p class="vm-hint">{{ inputHint }}</p>
    </div>

    <div class="vm-block">
      <div class="vm-label">输出方式（可多选）</div>
      <el-checkbox-group v-model="outputTypes" class="vm-group">
        <el-checkbox-button v-for="opt in VOICE_OUTPUT_OPTIONS" :key="opt.value" :value="opt.value">
          {{ opt.label }}
        </el-checkbox-button>
      </el-checkbox-group>
      <p class="vm-hint">{{ outputHint }}</p>
    </div>

    <template #footer>
      <el-button @click="visibleProxy = false">取消</el-button>
      <el-button type="primary" :disabled="!canConfirm" @click="handleConfirm">{{ confirmText }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { VOICE_INPUT_OPTIONS, VOICE_OUTPUT_OPTIONS } from "@/portal/stores/conversation";

const props = withDefaults(
  defineProps<{
    modelValue: boolean;
    /** 上次选择（默认选中） */
    inputTypes: string[];
    outputTypes: string[];
    /** 弹窗标题 / 确认按钮文案（文本页为「进入语音对话」，语音页为「应用」） */
    title?: string;
    confirmText?: string;
  }>(),
  {
    title: "选择对话模式",
    confirmText: "进入语音对话",
  },
);

const emit = defineEmits<{
  (e: "update:modelValue", v: boolean): void;
  (e: "confirm", inputTypes: string[], outputTypes: string[]): void;
}>();

const visibleProxy = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit("update:modelValue", v),
});

const inputTypes = ref<string[]>([...props.inputTypes]);
const outputTypes = ref<string[]>([...props.outputTypes]);

// 每次打开时同步上次选择
watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      inputTypes.value = [...props.inputTypes];
      outputTypes.value = [...props.outputTypes];
    }
  },
);

const canConfirm = computed(
  () =>
    inputTypes.value.length > 0 &&
    // 输出约束：TEXT/AUDIO 至少选一，AVATAR_2D 只能作为附加输出（后端仅在有 TEXT/AUDIO 输出时装配形象节点）
    (outputTypes.value.includes("TEXT") || outputTypes.value.includes("AUDIO")),
);

const inputHint = computed(() => {
  const hasAudio = inputTypes.value.includes("AUDIO");
  const hasText = inputTypes.value.includes("TEXT");
  if (hasAudio && hasText) return "麦克风持续收音，也可用输入框发送文字。";
  if (hasAudio) return "进入后自动开启麦克风，说话即自动识别，无需按键。";
  if (hasText) return "通过输入框发送文字。";
  return "请至少选择一种输入方式。";
});

const outputHint = computed(() => {
  const hasAudio = outputTypes.value.includes("AUDIO");
  const hasText = outputTypes.value.includes("TEXT");
  const hasAvatar = outputTypes.value.includes("AVATAR_2D");

  if (hasAvatar) {
    if (hasAudio && hasText) return "AI 回复将以语音播报并同步显示文字，同时显示虚拟形象。";
    if (hasAudio) return "AI 回复仅语音播报，并显示虚拟形象。";
    if (hasText) return "AI 回复仅显示文字，不播放语音，并显示虚拟形象。";
    return "虚拟形象需与语音或文本输出搭配使用，请至少选择其中一项。";
  }
  if (hasAudio && hasText) return "AI 回复将以语音播报并同步显示文字。";
  if (hasAudio) return "AI 回复仅语音播报。";
  if (hasText) return "AI 回复仅显示文字，不播放语音。";
  return "请至少选择一种输出方式。";
});

function handleConfirm() {
  if (!canConfirm.value) return;
  emit("confirm", [...inputTypes.value], [...outputTypes.value]);
}
</script>

<style scoped>
.vm-block {
  margin-bottom: 18px;
}

.vm-block:last-of-type {
  margin-bottom: 4px;
}

.vm-label {
  font-size: 13px;
  font-weight: 600;
  color: #333;
  margin-bottom: 10px;
}

.vm-group {
  width: 100%;
}

.vm-hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: #999;
  line-height: 1.6;
}
</style>
