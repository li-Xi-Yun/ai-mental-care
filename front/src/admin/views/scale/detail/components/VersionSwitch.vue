<template>
  <div class="version-switch">
    <span class="switch-label">编辑版本</span>
    <el-radio-group
      v-if="versions.length > 1"
      :model-value="selectedVersionId ?? undefined"
      size="small"
      @change="onChange"
    >
      <el-radio-button v-for="v in versions" :key="v.id" :value="v.id">
        {{ v.versionNo }}{{ v.isCurrent ? "·当前" : "" }}
      </el-radio-button>
    </el-radio-group>
    <span v-else-if="versions.length === 1" class="switch-single">{{ versions[0].versionNo }}</span>
    <span v-else class="muted">暂无版本</span>
  </div>
</template>

<script setup lang="ts">
import type { AdminScaleVersionVO } from "@/admin/api/scale/types";

defineProps<{
  versions: AdminScaleVersionVO[];
  selectedVersionId: number | null;
}>();
const emit = defineEmits<{ (e: "update:selectedVersionId", v: number | null): void }>();

function onChange(v: string | number | boolean | undefined) {
  emit("update:selectedVersionId", v == null ? null : Number(v));
}
</script>

<style scoped>
.version-switch {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.switch-label {
  font-size: 12px;
  color: #94a3b8;
}
.switch-single {
  font-size: 13px;
  font-weight: 600;
  color: var(--admin-primary);
}
.muted {
  color: #cbd5e1;
  font-size: 12px;
}
</style>
