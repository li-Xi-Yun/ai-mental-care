<template>
  <div class="stat-card">
    <div v-if="icon" :class="['stat-icon', toneClass]">
      <el-icon :size="22"><component :is="icon" /></el-icon>
    </div>
    <div class="stat-body">
      <div class="stat-num">{{ num }}</div>
      <div class="stat-label">{{ label }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Component } from "vue";
import type { StatTone } from "@/admin/types";

const props = withDefaults(
  defineProps<{
    /** 图标：全局注册的图标名（字符串）或组件 */
    icon?: string | Component;
    /** 渐变 tone，决定 stat-icon-* 类（indigo/green/red/yellow/gray） */
    tone?: StatTone;
    num?: string | number;
    label?: string;
  }>(),
  { tone: "indigo" },
);

const toneClass = `stat-icon-${props.tone}`;
</script>