<template>
  <div class="page-header">
    <div class="page-header-text">
      <h2 class="page-title">{{ title }}</h2>
      <p v-if="subtitle" class="page-subtitle">{{ subtitle }}</p>
    </div>
    <div class="page-header-actions">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ title: string; subtitle?: string }>(), {
  subtitle: "",
});
</script>
