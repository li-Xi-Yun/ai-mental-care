<template>
  <div class="es-page">
    <header class="es-page-header">
      <div>
        <div class="es-breadcrumb">
          <router-link to="/diagnosis">心理诊断</router-link>
          <span>›</span>
          <span class="cur">{{ conversationName || `#${conversationId}` }}</span>
        </div>
        <h2 class="es-title">{{ conversationName || `#${conversationId}` }} — 诊断记录</h2>
      </div>
    </header>

    <div v-if="loading" class="es-list-loading">
      <div v-for="i in 3" :key="i" class="es-sk-row"></div>
    </div>

    <el-empty v-else-if="loadError" class="es-empty-wrap" description="评估记录加载失败，请稍后重试">
      <el-button type="primary" @click="fetchList">重新加载</el-button>
    </el-empty>

    <el-empty v-else-if="records.length === 0" class="es-empty-wrap" description="该会话暂无评估报告" />

    <template v-else>
      <div class="es-table-wrap">
        <table class="es-table">
          <thead>
            <tr>
              <th>诊断ID</th>
              <th>会话ID</th>
              <th>轮次</th>
              <th>创建时间</th>
              <th>更新时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="record in records" :key="record.id" @click="goDetail(record.id)">
              <td>#{{ record.id }}</td>
              <td>{{ conversationId }}</td>
              <td>{{ record.roundNum ? `第${record.roundNum}轮` : "评估报告" }}</td>
              <td>{{ formatDateTime(record.createdTime) }}</td>
              <td>{{ formatDateTime(record.updatedTime) }}</td>
              <td><span class="es-link">查看详情 →</span></td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="total > pageSize" class="es-pagination">
        <el-pagination
          v-model:current-page="pageNum"
          :page-size="pageSize"
          :total="total"
          :layout="PAGINATION_LAYOUT"
          background
          @current-change="fetchList"
        />
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import "@/shared/styles/emotion/emotion-design.css";
import { getEmotionDiagnosisListByConversation } from "@/portal/api/conversation/emotion-diagnosis";
import type { EmotionDiagnosisListVO } from "@/portal/api/conversation/emotion-diagnosis";
import { dayjs } from "@/shared/utils";
import { DEFAULT_PAGE_SIZE, PAGINATION_LAYOUT } from "@/shared/api/config";

const route = useRoute();
const router = useRouter();
// 路由 /diagnosis/:sessionId；会话ID为 19 位雪花ID，按字符串直传避免 Number() 丢精度
const conversationId = String(route.params.sessionId ?? "");
const conversationName = ref(route.query.name ?? "");

const records = ref<EmotionDiagnosisListVO[]>([]);
const loading = ref(false);
const loadError = ref(false);
const pageNum = ref(1);
const pageSize = DEFAULT_PAGE_SIZE;
const total = ref(0);

function formatDateTime(v?: string): string {
  if (!v) return "--";
  const d = dayjs(v);
  return d.isValid() ? d.format("YYYY-MM-DD HH:mm") : String(v);
}

async function fetchList() {
  loading.value = true;
  loadError.value = false;
  try {
    const res = await getEmotionDiagnosisListByConversation(conversationId, {
      pageNum: pageNum.value,
      pageSize: pageSize,
    });
    const page = res.data?.data;
    records.value = page?.records ?? [];
    total.value = page?.total ?? 0;
  } catch {
    records.value = [];
    total.value = 0;
    loadError.value = true;
  } finally {
    loading.value = false;
  }
}

function goDetail(recordId?: number) {
  if (recordId == null) return;
  router.push(`/diagnosis/${conversationId}/${recordId}`);
}

onMounted(() => {
  void fetchList();
});
</script>
