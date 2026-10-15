<template>
  <aside class="conversation-list-panel" :style="listPanelStyle">
    <div class="sl-header">
      <span class="sl-title">会话列表</span>
      <button class="new-btn" title="新建会话" @click="emit('new')">
        <el-icon :size="18"><Plus /></el-icon>
      </button>
    </div>

    <div class="sl-body">
      <template v-if="loading">
        <div class="list-skeleton">
          <div class="sk-row" v-for="i in 4" :key="i">
            <div class="sk-avatar"></div>
            <div class="sk-text">
              <div class="sk-line" style="width: 80%"></div>
              <div class="sk-line" style="width: 45%"></div>
            </div>
          </div>
        </div>
      </template>

      <template v-else-if="conversationList.length">
        <div
          v-for="item in conversationList"
          :key="item.id"
          class="sl-item"
          :class="{ active: currentConversationId === item.id }"
          @click="emit('select', item.id)"
        >
          <div class="sl-icon">
            <el-icon><ChatDotRound /></el-icon>
          </div>
          <div class="sl-main">
            <div class="sl-name">{{ item.name }}</div>
            <div class="sl-meta">
              <span v-if="item.currentRound" class="sl-round">第{{ item.currentRound }}轮</span>
              <span class="sl-time">{{ listTime(item.lastActiveTime) }}</span>
            </div>
          </div>
          <el-dropdown trigger="click" @command="(cmd: string) => emit('command', item, cmd)">
            <span class="sl-more" @click.stop>
              <el-icon><More /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="rename">
                  <el-icon><EditPen /></el-icon>重命名
                </el-dropdown-item>
                <el-dropdown-item command="delete" divided>
                  <el-icon><Delete /></el-icon>删除
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </template>

      <div v-else class="list-empty">
        <p>还没有会话</p>
        <el-button type="primary" size="small" round @click="emit('new')">开启第一段倾诉</el-button>
      </div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { Plus, ChatDotRound, More, EditPen, Delete } from "@element-plus/icons-vue";
import dayjs from "@/shared/utils/dayjs";
import type { ConversationItem } from "@/portal/api/conversation/types";

const props = defineProps<{
  conversationList: ConversationItem[];
  currentConversationId: string;
  loading: boolean;
  showPanel: boolean;
  /** 面板宽度（px，可拖拽调整） */
  width: number;
}>();

const emit = defineEmits<{
  (e: "select", id: string): void;
  (e: "new"): void;
  (e: "command", item: ConversationItem, cmd: string): void;
}>();

const listPanelStyle = computed(() => ({
  width: props.showPanel ? `${props.width}px` : "0px",
  opacity: props.showPanel ? 1 : 0,
}));

function listTime(v?: string): string {
  if (!v) return "刚刚";
  const d = dayjs(v);
  if (!d.isValid()) return String(v);
  const now = dayjs();
  if (d.isSame(now, "day")) return d.format("HH:mm");
  if (d.isSame(now.subtract(1, "day"), "day")) return "昨天";
  return d.format("M月D日");
}
</script>

<style scoped>
.conversation-list-panel {
  background: #fff;
  border-right: 1px solid #e5e7f0;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  overflow: hidden;
  transition: width 0.25s ease, opacity 0.25s ease;
}

.sl-header {
  padding: 16px 16px 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #f0f1f7;
  flex-shrink: 0;
}

.sl-title {
  font-size: 14px;
  font-weight: 700;
  color: #1e1e2e;
}

.new-btn {
  cursor: pointer;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: none;
  background: linear-gradient(135deg, #6c63ff, #3f3d9e);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  font-family: inherit;
  flex-shrink: 0;
  box-shadow: 0 2px 8px rgba(108, 99, 255, 0.3);
}

.new-btn:hover {
  transform: rotate(90deg) scale(1.05);
  box-shadow: 0 4px 12px rgba(108, 99, 255, 0.4);
}

.sl-body {
  flex: 1;
  overflow-y: auto;
  padding: 6px 10px 10px;
}

.sl-body::-webkit-scrollbar {
  width: 4px;
}

.sl-body::-webkit-scrollbar-thumb {
  background: #dddde8;
  border-radius: 4px;
}

.sl-item {
  padding: 10px 10px 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  margin-bottom: 2px;
  display: flex;
  align-items: center;
  gap: 10px;
  position: relative;
  transition: background 0.18s ease, box-shadow 0.18s ease;
}

.sl-item:hover {
  background: #f5f6fa;
}

.sl-item.active {
  background: linear-gradient(135deg, #f0eeff 0%, #f7f6ff 100%);
  box-shadow: inset 3px 0 0 #6c63ff;
}

.sl-icon {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  background: linear-gradient(135deg, #6c63ff, #818cf8);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  flex-shrink: 0;
}

.sl-main {
  flex: 1;
  min-width: 0;
}

.sl-name {
  font-size: 13px;
  font-weight: 500;
  color: #1e1e2e;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sl-item.active .sl-name {
  color: #6c63ff;
  font-weight: 600;
}

.sl-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 3px;
}

.sl-round {
  font-size: 11px;
  color: #6c63ff;
  background: #f0eeff;
  padding: 0 6px;
  border-radius: 8px;
}

.sl-time {
  font-size: 11px;
  color: #9a9ab0;
}

.sl-more {
  opacity: 0;
  cursor: pointer;
  color: #9a9ab0;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: opacity 0.15s ease, background 0.15s ease, color 0.15s ease;
  flex-shrink: 0;
}

.sl-item:hover .sl-more {
  opacity: 1;
}

.sl-more:hover {
  background: #e4e6f0;
  color: #1e1e2e;
}

.list-empty {
  text-align: center;
  padding: 40px 8px;
  color: #9a9ab0;
  font-size: 13px;
}

.list-empty p {
  margin-bottom: 12px;
}

.list-skeleton {
  padding: 8px 4px;
}

.list-skeleton .sk-row {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 10px 0;
}

.list-skeleton .sk-avatar {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  background: #f0f1f7;
}

.list-skeleton .sk-text {
  flex: 1;
}

.list-skeleton .sk-line {
  height: 10px;
  border-radius: 5px;
  background: linear-gradient(90deg, #f0f0f6, #fafafd, #f0f0f6);
  background-size: 200% 100%;
  animation: shimmer 1.2s infinite;
  margin: 4px 0;
}

@keyframes shimmer {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}
</style>