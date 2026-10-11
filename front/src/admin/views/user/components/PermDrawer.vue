<template>
  <el-drawer :model-value="modelValue" :title="`临时权限 · ${target?.username ?? ''}`" size="560px" @update:model-value="emit('update:modelValue', $event)">
    <div v-loading="loading" class="drawer-body">
      <template v-if="permList.length">
        <el-timeline class="perm-timeline">
          <el-timeline-item
            v-for="item in permList"
            :key="item.id"
            :type="permTimelineType(item.status)"
            :hollow="item.status !== 0"
            placement="top"
          >
            <div class="perm-timeline-card">
              <div class="perm-timeline-head">
                <span class="perm-name">{{ item.permissionName }}</span>
                <el-tag :type="permTagType(item.status)" size="small" effect="light" round>
                  {{ permStatusText(item.status) }}
                </el-tag>
              </div>
              <div class="perm-perms">
                <el-tag v-for="p in permCodes(item)" :key="p" size="small" class="perm-code-tag" effect="plain" type="info">
                  {{ p }}
                </el-tag>
                <span v-if="!permCodes(item).length" class="muted-text">无权限码</span>
              </div>
              <div class="perm-group">
                <span class="muted-text">所属分组：</span>{{ item.groupName || "—" }}
              </div>
              <div class="perm-meta-grid">
                <div class="perm-meta-item">
                  <span class="muted-text">生效时间</span>
                  <span class="perm-meta-val">{{ formatTime(item.startTime) }}</span>
                </div>
                <div class="perm-meta-item">
                  <span class="muted-text">过期时间</span>
                  <span class="perm-meta-val">{{ formatTime(item.expireTime) }}</span>
                </div>
                <div class="perm-meta-item">
                  <span class="muted-text">授予人</span>
                  <span class="perm-meta-val">{{ item.grantUsername || "—" }}</span>
                </div>
                <div class="perm-meta-item">
                  <span class="muted-text">授予时间</span>
                  <span class="perm-meta-val">{{ formatTime(item.createdTime) }}</span>
                </div>
              </div>
              <div class="perm-reason">
                <span class="muted-text">授予原因：</span>{{ item.grantReason || "—" }}
              </div>
            </div>
          </el-timeline-item>
        </el-timeline>
      </template>
      <el-empty v-else description="该用户暂无临时权限" :image-size="80" />
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import type { TempPermissionVO } from "@/admin/api/admin/user-manage";

defineProps<{
  modelValue: boolean;
  loading: boolean;
  target?: { username?: string } | null;
  permList: TempPermissionVO[];
  permCodes: (item: TempPermissionVO) => string[];
  permStatusText: (status?: number) => string;
  permTagType: (status?: number) => "success" | "danger" | "info";
  permTimelineType: (status?: number) => "primary" | "danger" | "info";
  formatTime: (v?: string) => string;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", v: boolean): void;
}>();
</script>

<style scoped>
.drawer-body {
  min-height: 200px;
}

.perm-timeline {
  padding-left: 4px;
}

.perm-timeline-card {
  background: #f8fafc;
  border: 1px solid #eef1f6;
  border-radius: 10px;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.perm-timeline-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.perm-name {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}

.perm-perms {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.perm-code-tag {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.perm-group {
  font-size: 13px;
  color: #475569;
}

.perm-meta-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px 16px;
  background: #fff;
  border-radius: 8px;
  padding: 10px 12px;
}

.perm-meta-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.perm-meta-val {
  font-size: 13px;
  color: #1e293b;
}

.perm-reason {
  font-size: 13px;
  color: #475569;
}

.muted-text {
  color: #94a3b8;
}
</style>