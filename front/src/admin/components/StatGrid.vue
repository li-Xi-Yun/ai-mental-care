<template>
  <div class="stat-grid" :style="columnsStyle">
    <slot />
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";

const props = withDefaults(
  defineProps<{
    /** 列数，默认 4 列；传 0 表示沿用默认 grid 布局 */
    columns?: number;
  }>(),
  { columns: 0 },
);

const columnsStyle = computed(() => (props.columns > 0 ? { gridTemplateColumns: `repeat(${props.columns}, minmax(0, 1fr))` } : undefined));
</script>