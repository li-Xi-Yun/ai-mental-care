<template>
  <div class="es-page">
    <header class="es-page-header">
      <div>
        <h2 class="es-title">心理诊断</h2>
        <p class="es-subtitle">AI 根据对话生成的评估报告沉淀于此，持续追踪你的情绪状态变化</p>
      </div>
      <div class="es-search-row">
        <input
          v-model="keyword"
          class="es-search-input"
          type="text"
          placeholder="搜索会话名称"
          @keyup.enter="handleSearch"
        />
        <button type="button" class="es-btn-primary" @click="handleSearch">搜索</button>
      </div>
    </header>

    <div v-if="loading" class="es-list-loading">
      <div v-for="i in 3" :key="i" class="es-sk-row"></div>
    </div>

    <template v-else>
      <div v-if="!userStore.token" class="es-empty-wrap">
        <el-empty description="登录后即可查看评估记录">
          <el-button type="primary" @click="userStore.openLoginDialog('login')">去登录</el-button>
        </el-empty>
      </div>
      <div v-else-if="loadError" class="es-empty-wrap">
        <el-empty description="评估列表加载失败，请稍后重试">
          <el-button type="primary" @click="fetchList">重新加载</el-button>
        </el-empty>
      </div>
      <div v-else-if="sessions.length === 0" class="es-empty-wrap">
        <el-empty description="暂未生成评估记录，快去 AI 对话中聊聊吧" />
      </div>
      <template v-else>
        <div class="es-table-wrap">
          <table class="es-table">
            <thead>
              <tr>
                <th>会话名称</th>
                <th>最新诊断轮次</th>
                <th>诊断次数</th>
                <th>最近诊断时间</th>
                <th>会话创建时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="session in sessions" :key="session.conversationId" @click="goConversationRecords(session)">
                <td>
                  <span class="es-emoji">{{ emojiFor(session.conversationName) }}</span>
                  {{ session.conversationName }}
                </td>
                <td>{{ roundText(session.latestRoundNum) }}</td>
                <td>{{ session.diagnosisCount ?? 0 }}</td>
                <td>{{ formatDateTime(session.diagnosisUpdatedTime) }}</td>
                <td>{{ formatDateTime(session.conversationCreatedTime) }}</td>
                <td>
                  <span class="es-link">查看诊断 →</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-if="total > 0" class="es-pagination">
          <el-pagination
            v-model:current-page="pageNum"
            :page-size="PAGE_SIZE"
            :total="total"
            :layout="PAGINATION_LAYOUT"
            background
            @current-change="fetchList"
          />
        </div>
      </template>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted, watch } from "vue";
import { useRouter } from "vue-router";
import { useUserStore } from "@/portal/stores/user";
import "@/shared/styles/emotion/emotion-design.css";
import { dayjs } from "@/shared/utils";
import { DEFAULT_PAGE_SIZE, PAGINATION_LAYOUT } from "@/shared/api/config";
import { useDiagnosisList } from "@/portal/composables/useDiagnosisList";
import type { DiagnosisConversationVO } from "@/portal/api/conversation/emotion-diagnosis";

const PAGE_SIZE = DEFAULT_PAGE_SIZE;
const router = useRouter();
const userStore = useUserStore();

const {
  keyword,
  pageNum,
  total,
  sessions,
  loading,
  loadError,
  fetchList,
  handleSearch,
} = useDiagnosisList();

/** 会话名关键字 → emoji 映射（按序匹配，首个命中生效） */
const SESSION_EMOJI: Array<{ keywords: string[]; emoji: string }> = [
  { keywords: ["睡眠", "失眠", "入睡"], emoji: "🌙" },
  { keywords: ["工作", "压力", "职场", "加班"], emoji: "💼" },
  { keywords: ["考试", "学业", "学习"], emoji: "📚" },
  { keywords: ["焦虑", "紧张"], emoji: "😰" },
  { keywords: ["关系", "感情", "恋"], emoji: "💕" },
  { keywords: ["低落", "抑郁", "难过"], emoji: "🌧️" },
  { keywords: ["成长", "改变"], emoji: "🌱" },
  { keywords: ["社交", "社恐", "朋友"], emoji: "😶" },
  { keywords: ["家庭", "家人"], emoji: "🏠" },
  { keywords: ["健康", "身体"], emoji: "🛡️" },
];

function emojiFor(name?: string): string {
  const text = name ?? "";
  for (const { keywords, emoji } of SESSION_EMOJI) {
    if (keywords.some((k) => text.includes(k))) return emoji;
  }
  return "💬";
}

function formatDateTime(v?: string): string {
  if (!v) return "--";
  const d = dayjs(v);
  return d.isValid() ? d.format("YYYY-MM-DD HH:mm") : String(v);
}

function roundText(v?: number): string {
  return v != null ? `第${v}轮` : "--";
}

function goConversationRecords(session: DiagnosisConversationVO) {
  router.push({
    path: `/diagnosis/${session.conversationId}`,
    query: session.conversationName ? { name: session.conversationName } : undefined,
  });
}

watch(
  () => userStore.token,
  (t) => { if (t && sessions.value.length === 0 && !loading.value) void fetchList(); },
);

onMounted(() => { if (userStore.token) void fetchList(); });
</script>