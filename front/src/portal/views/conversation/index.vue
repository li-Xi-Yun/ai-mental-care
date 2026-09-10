<template>
  <div class="conversation-page">
    <div class="conversation-container">
      <transition name="slide-left">
        <div v-show="showConversationList" class="conversation-list-panel">
          <div class="list-header">
            <span class="list-title">对话</span>
            <el-button type="primary" size="small" circle @click="handleNewConversation">
              <el-icon><Plus /></el-icon>
            </el-button>
          </div>
          <div class="list-body">
            <div
              v-for="conv in conversationList"
              :key="conv.id"
              class="conversation-item"
              :class="{ active: currentConversationId === conv.id }"
              @click="selectConversation(conv.id)"
            >
              <div class="conv-title">{{ conv.title }}</div>
              <div class="conv-time">{{ conv.time }}</div>
            </div>
          </div>
        </div>
      </transition>

      <div class="chat-panel">
        <div class="chat-header">
          <div class="chat-header-left">
            <el-button text @click="showConversationList = !showConversationList">
              <el-icon :size="18"><Operation /></el-icon>
            </el-button>
            <span class="chat-title">{{ currentConversation?.title || "AI 对话" }}</span>
          </div>
          <div class="chat-header-right">
            <el-button text @click="showEmotionPanel = !showEmotionPanel">
              <el-icon :size="18"><DataAnalysis /></el-icon>
            </el-button>
          </div>
        </div>

        <div class="chat-body" ref="chatBodyRef">
          <div
            v-for="msg in messageList"
            :key="msg.id"
            class="message-row"
            :class="msg.role"
          >
            <div v-if="msg.role === 'assistant'" class="message-avatar assistant-avatar">AI</div>
            <div class="message-bubble" :class="msg.role">
              {{ msg.content }}
            </div>
            <div v-if="msg.role === 'user'" class="message-avatar user-avatar">我</div>
          </div>
        </div>

        <div class="chat-input">
          <el-input
            v-model="inputMessage"
            type="textarea"
            :rows="1"
            :autosize="{ minRows: 1, maxRows: 4 }"
            placeholder="输入消息..."
            resize="none"
            @keydown.enter.exact.prevent="handleSend"
          />
          <el-button text class="input-icon-btn">
            <el-icon :size="20"><Microphone /></el-icon>
          </el-button>
          <el-button type="primary" @click="handleSend">发送</el-button>
        </div>
      </div>

      <transition name="slide-right">
        <div v-show="showEmotionPanel" class="emotion-panel">
          <div class="emotion-header">
            <span class="emotion-title">情绪分析</span>
            <el-button text size="small" @click="showEmotionPanel = false">
              <el-icon><Close /></el-icon>
            </el-button>
          </div>
          <div class="emotion-body">
            <div class="emotion-section">
              <div class="section-title">当前情绪</div>
              <div class="emotion-chart-row">
                <div class="emotion-ring">
                  <svg viewBox="0 0 100 100" class="ring-svg">
                    <circle cx="50" cy="50" r="40" fill="none" stroke="#f0f0f0" stroke-width="8" />
                    <circle cx="50" cy="50" r="40" fill="none" stroke="#6c63ff" stroke-width="8"
                      stroke-dasharray="100.53 150.80" stroke-dashoffset="0" />
                    <circle cx="50" cy="50" r="40" fill="none" stroke="#ff6b6b" stroke-width="8"
                      stroke-dasharray="62.83 188.50" stroke-dashoffset="-100.53" />
                    <circle cx="50" cy="50" r="40" fill="none" stroke="#52c41a" stroke-width="8"
                      stroke-dasharray="50.27 201.06" stroke-dashoffset="-163.36" />
                    <circle cx="50" cy="50" r="40" fill="none" stroke="#fa8c16" stroke-width="8"
                      stroke-dasharray="37.70 213.63" stroke-dashoffset="-213.63" />
                  </svg>
                </div>
                <div class="emotion-legend">
                  <div class="legend-item"><span class="dot" style="background:#6c63ff"></span>平静 40%</div>
                  <div class="legend-item"><span class="dot" style="background:#ff6b6b"></span>焦虑 25%</div>
                  <div class="legend-item"><span class="dot" style="background:#52c41a"></span>开心 20%</div>
                  <div class="legend-item"><span class="dot" style="background:#fa8c16"></span>低落 15%</div>
                </div>
              </div>
            </div>

            <div class="emotion-section">
              <div class="section-title">情绪趋势</div>
              <div class="emotion-trend">
                <svg viewBox="0 0 200 60" class="trend-svg">
                  <polyline points="10,45 35,30 60,40 85,15 110,32 135,10 160,25 185,8"
                    fill="none" stroke="#6c63ff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
                  <polyline points="10,45 35,30 60,40 85,15 110,32 135,10 160,25 185,8"
                    fill="none" stroke="#6c63ff" stroke-width="0" opacity="0.1" />
                </svg>
              </div>
            </div>

            <div class="emotion-section">
              <div class="section-title">关键词</div>
              <div class="emotion-keywords">
                <el-tag type="warning" size="small">工作压力</el-tag>
                <el-tag type="danger" size="small">失眠</el-tag>
                <el-tag size="small">焦虑</el-tag>
                <el-tag type="success" size="small">倾诉</el-tag>
              </div>
            </div>

            <div class="emotion-section">
              <div class="section-title">诊断建议</div>
              <div class="emotion-advice">
                建议尝试放松训练和规律作息，如持续失眠建议就医。
              </div>
            </div>
          </div>
        </div>
      </transition>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick } from "vue";
import { Plus, Operation, DataAnalysis, Close, Microphone } from "@element-plus/icons-vue";
import { CONVERSATION_LIST_WIDTH, EMOTION_PANEL_WIDTH, PORTAL_HEADER_HEIGHT } from "@/shared/api/config";

const showConversationList = ref(true);
const showEmotionPanel = ref(true);
const currentConversationId = ref("1");
const inputMessage = ref("");
const chatBodyRef = ref<HTMLElement>();

const conversationList = ref([
  { id: "1", title: "心情困扰", time: "今天 14:30" },
  { id: "2", title: "工作压力", time: "昨天" },
  { id: "3", title: "睡眠问题", time: "3天前" },
  { id: "4", title: "人际关系", time: "5天前" },
  { id: "5", title: "家庭沟通", time: "1周前" },
]);

const currentConversation = computed(() => {
  return conversationList.value.find((c) => c.id === currentConversationId.value);
});

const messageList = ref([
  { id: "1", role: "assistant", content: "你好！我是你的AI心理健康助手，有什么想和我聊聊的吗？" },
  { id: "2", role: "user", content: "最近工作压力很大，经常失眠..." },
  { id: "3", role: "assistant", content: "我理解工作压力带来的困扰。能具体说说是什么让你感到压力吗？" },
  { id: "4", role: "user", content: "项目截止日期快到了，每天加班到很晚，躺在床上也睡不着" },
  { id: "5", role: "assistant", content: "长期高压和睡眠不足确实会形成恶性循环。我们可以先从放松技巧开始，你愿意尝试一下深呼吸练习吗？" },
]);

function selectConversation(id: string) {
  currentConversationId.value = id;
}

function handleNewConversation() {
  console.log("新建对话");
}

function handleSend() {
  if (!inputMessage.value.trim()) return;
  messageList.value.push({
    id: String(Date.now()),
    role: "user",
    content: inputMessage.value,
  });
  inputMessage.value = "";
  nextTick(() => {
    if (chatBodyRef.value) {
      chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight;
    }
  });
}
</script>

<style scoped>
.conversation-page {
  height: calc(100vh - v-bind(PORTAL_HEADER_HEIGHT + 'px'));
  overflow: hidden;
}

.conversation-container {
  display: flex;
  height: 100%;
}

.conversation-list-panel {
  width: v-bind(CONVERSATION_LIST_WIDTH + 'px');
  background: #fff;
  border-right: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.list-header {
  padding: 12px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #f0f0f0;
}

.list-title {
  font-size: 15px;
  font-weight: 600;
  color: #333;
}

.list-body {
  flex: 1;
  overflow-y: auto;
}

.conversation-item {
  padding: 12px 16px;
  cursor: pointer;
  border-bottom: 1px solid #f5f5f5;
  transition: background 0.2s;
}

.conversation-item:hover {
  background: #f8f7ff;
}

.conversation-item.active {
  background: #f0eeff;
}

.conv-title {
  font-size: 13px;
  font-weight: 500;
  color: #333;
  margin-bottom: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-time {
  font-size: 11px;
  color: #999;
}

.chat-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fafafa;
  min-width: 0;
}

.chat-header {
  height: 48px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
}

.chat-header-left,
.chat-header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.chat-title {
  font-size: 14px;
  font-weight: 500;
  color: #333;
}

.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
}

.message-row {
  display: flex;
  margin-bottom: 16px;
  align-items: flex-start;
  gap: 8px;
}

.message-row.user {
  justify-content: flex-end;
}

.message-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  flex-shrink: 0;
}

.assistant-avatar {
  background: linear-gradient(135deg, #6c63ff, #3f3d9e);
  color: #fff;
}

.user-avatar {
  background: #52c41a;
  color: #fff;
}

.message-bubble {
  max-width: 70%;
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}

.message-bubble.assistant {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px 12px 12px 4px;
  color: #333;
}

.message-bubble.user {
  background: #6c63ff;
  color: #fff;
  border-radius: 12px 12px 4px 12px;
}

.chat-input {
  padding: 12px 16px;
  background: #fff;
  border-top: 1px solid #e5e7eb;
  display: flex;
  align-items: flex-end;
  gap: 8px;
}

.chat-input :deep(.el-textarea) {
  flex: 1;
}

.chat-input :deep(.el-textarea__inner) {
  border-radius: 8px;
  min-height: 36px !important;
}

.input-icon-btn {
  color: #999;
}

.emotion-panel {
  width: v-bind(EMOTION_PANEL_WIDTH + 'px');
  background: #fff;
  border-left: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.emotion-header {
  padding: 12px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #f0f0f0;
}

.emotion-title {
  font-size: 14px;
  font-weight: 600;
  color: #333;
}

.emotion-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.emotion-section {
  margin-bottom: 20px;
}

.section-title {
  font-size: 13px;
  font-weight: 500;
  color: #333;
  margin-bottom: 8px;
}

.emotion-chart-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.emotion-ring {
  width: 80px;
  height: 80px;
  flex-shrink: 0;
}

.ring-svg {
  width: 100%;
  height: 100%;
  transform: rotate(-90deg);
}

.emotion-legend {
  font-size: 12px;
  line-height: 2;
  color: #666;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}

.emotion-trend {
  background: #fafafa;
  border-radius: 8px;
  padding: 8px;
}

.trend-svg {
  width: 100%;
  height: 50px;
}

.emotion-keywords {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.emotion-advice {
  font-size: 13px;
  color: #666;
  line-height: 1.7;
  background: #f8f7ff;
  padding: 10px;
  border-radius: 8px;
}

.slide-left-enter-active,
.slide-left-leave-active {
  transition: all 0.3s ease;
}

.slide-left-enter-from,
.slide-left-leave-to {
  margin-left: -220px;
  opacity: 0;
}

.slide-right-enter-active,
.slide-right-leave-active {
  transition: all 0.3s ease;
}

.slide-right-enter-from,
.slide-right-leave-to {
  margin-right: -260px;
  opacity: 0;
}
</style>