<!-- 角色权限分配弹窗：分组平铺复选块（Blog 风格），勾选状态由父级持有 -->
<template>
  <el-dialog
    :model-value="modelValue"
    title="分配权限"
    width="560"
    align-center
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
    @closed="emit('closed')"
  >
    <p class="rp-perm-desc">
      为角色 <span class="rp-perm-role">{{ roleName }}</span>
      <template v-if="roleKey">（<code class="rp-perm-rolekey">{{ roleKey }}</code>）</template>
      分配权限，保存后全量覆盖已有权限。
    </p>

    <div v-loading="!!loading" class="rp-perm-scroll">
      <template v-if="groups.length">
        <section v-for="group in groups" :key="group.groupName" class="rp-perm-group">
          <p class="rp-perm-group-title">
            <el-icon :size="13"><FolderOpened /></el-icon>{{ group.groupName }}
          </p>
          <div class="rp-perm-list">
            <label
              v-for="perm in group.permissions"
              :key="perm.id"
              class="rp-perm-item"
              :class="{ selected: selectedIds.includes(String(perm.id)) }"
            >
              <input
                class="rp-perm-check"
                type="checkbox"
                :checked="selectedIds.includes(String(perm.id))"
                @change="emit('toggle', String(perm.id))"
              />
              <span class="rp-perm-name">{{ perm.name }}</span>
              <code class="rp-perm-code">{{ perm.perms }}</code>
              <span v-if="perm.status === 1" class="rp-perm-off">停用</span>
            </label>
          </div>
        </section>
      </template>
      <p v-else-if="!loading" class="rp-perm-empty">暂无可用权限</p>
    </div>

    <template #footer>
      <el-button :disabled="!!submitting" @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" :disabled="!!loading || !!submitting" @click="emit('submit')">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { FolderOpened } from "@element-plus/icons-vue";
import type { PermissionGroup } from "../useRoleList";

defineProps<{
  modelValue: boolean;
  roleName?: string;
  roleKey?: string;
  loading?: boolean;
  submitting?: boolean;
  groups: PermissionGroup[];
  /** 已选真实权限 id 集合（字符串形态保存，避免雪花 ID 精度丢失） */
  selectedIds: string[];
}>();
const emit = defineEmits<{
  (e: "update:modelValue", v: boolean): void;
  /** 勾选 / 取消勾选单个权限 id */
  (e: "toggle", id: string): void;
  /** 保存（父级持有完整已选 id 数组） */
  (e: "submit"): void;
  (e: "closed"): void;
}>();
</script>

<style scoped>
.rp-perm-desc {
  margin: 0 0 14px;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--admin-text-secondary, #6b7280);
}
.rp-perm-role {
  font-weight: 600;
  color: var(--admin-text-primary, #1f2937);
}
.rp-perm-rolekey {
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 12px;
  color: var(--admin-primary, #4f46e5);
  background: var(--admin-primary-soft, #eef2ff);
  padding: 1px 6px;
  border-radius: 5px;
}
.rp-perm-scroll {
  max-height: 380px;
  overflow-y: auto;
  padding-right: 4px;
}
.rp-perm-group + .rp-perm-group {
  margin-top: 16px;
}
.rp-perm-group-title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0 0 8px;
  font-size: 12px;
  font-weight: 600;
  color: var(--admin-text-secondary, #6b7280);
}
.rp-perm-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.rp-perm-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 12px;
  border: 1px solid var(--admin-border-strong, #e5e7eb);
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.2s ease, background 0.2s ease;
}
.rp-perm-item:hover {
  background: #f9fafb;
}
.rp-perm-item.selected {
  border-color: var(--admin-primary, #4f46e5);
  background: var(--admin-primary-soft, #eef2ff);
}
.rp-perm-check {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
  accent-color: var(--admin-primary, #4f46e5);
  cursor: pointer;
}
.rp-perm-name {
  font-size: 13px;
  color: #374151;
  flex-shrink: 0;
}
.rp-perm-code {
  flex: 1;
  min-width: 0;
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 11px;
  color: var(--admin-text-muted, #9ca3af);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rp-perm-off {
  flex-shrink: 0;
  padding: 1px 8px;
  border-radius: 999px;
  background: #f3f4f6;
  font-size: 10px;
  color: var(--admin-text-secondary, #6b7280);
}
.rp-perm-empty {
  margin: 0;
  padding: 32px 0;
  text-align: center;
  font-size: 13px;
  color: var(--admin-text-muted, #9ca3af);
}
</style>
