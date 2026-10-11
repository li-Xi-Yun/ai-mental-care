<template>
  <span class="status-badge" :style="badgeStyle">
    <span v-if="dot" class="status-dot" :style="dotStyle" />
    <slot>{{ label }}</slot>
  </span>
</template>

<script setup lang="ts">
import { computed } from "vue";

const props = withDefaults(
  defineProps<{
    /** 主色（文字 + 圆点） */
    color?: string;
    /** 背景色 */
    bg?: string;
    /** 边框色 */
    border?: string;
    label?: string;
    dot?: boolean;
  }>(),
  { dot: true },
);

const badgeStyle = computed(() => ({
  ...(props.color ? { color: props.color } : {}),
  ...(props.bg ? { background: props.bg } : {}),
  ...(props.border ? { borderColor: props.border } : {}),
}));

const dotStyle = computed(() => (props.color ? { background: props.color } : {}));
</script>