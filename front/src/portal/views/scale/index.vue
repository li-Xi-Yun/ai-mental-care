<template>
  <div class="scale-list-page">
    <header class="page-header">
      <div class="header-copy">
        <div class="header-top-row">
          <div class="header-eyebrow">专业心理量表</div>
          <el-button
            class="records-entry"
            type="default"
            size="small"
            round
            @click="router.push('/scale/records')"
          >
            我的测评记录
          </el-button>
        </div>
        <h2 class="page-title">量表测评</h2>
        <p class="page-subtitle">选择一份量表，开启自我探索之旅</p>
      </div>
      <div class="search-row">
        <el-input
          v-model="keyword"
          class="search-input"
          placeholder="按量表名称搜索"
          clearable
          :prefix-icon="Search"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
      </div>
    </header>

    <div class="category-bar">
      <div v-if="categoriesLoading" class="cat-skeleton"><div class="cat-sk"></div><div class="cat-sk"></div><div class="cat-sk"></div><div class="cat-sk"></div></div>
      <template v-else-if="categoriesError">
        <el-tag type="danger" size="small">分类加载失败</el-tag>
        <el-button text type="primary" size="small" @click="loadCategories">重试</el-button>
      </template>
      <template v-else>
        <button
          v-for="tab in categoryTabs"
          :key="tab.key"
          class="cat-tag"
          :class="{ active: activeCategory === tab.key }"
          @click="updateCategory(tab.key)"
        >
          <span class="cat-name">{{ tab.label }}</span>
          <span class="cat-count">{{ tab.scaleCount }}</span>
        </button>
      </template>
    </div>

    <div v-if="scalesLoading" class="list-loading">
      <div v-for="i in 6" :key="i" class="sk-card">
        <span class="sk-strip"></span>
        <div class="sk-content">
          <div class="sk-line sk-lg"></div>
          <div class="sk-line sk-sm"></div>
        </div>
      </div>
    </div>

    <template v-else>
      <el-empty v-if="scaleList.length === 0" class="empty-wrap" description="暂未找到量表，换个关键词试试" />
      <div v-else class="scale-grid">
        <div
          v-for="scale in scaleList"
          :key="scale.id"
          class="scale-card"
          @click="guardAndStart(scale)"
        >
          <span class="card-strip" :style="{ backgroundImage: tintFor(scale.scaleCategoryId ?? 0, scale.id).gradient }"></span>
          <div class="card-body">
            <div class="card-icon" :style="{ background: tintFor(scale.scaleCategoryId ?? 0, scale.id).soft }">
              <span class="imoji">{{ iconFor(scale) }}</span>
            </div>
            <div class="card-content">
              <h4 class="card-title">{{ scale.scaleName }}</h4>
              <p class="card-desc">{{ scale.description || "暂无介绍" }}</p>
              <div class="card-footer">
                <span class="footer-meta"><el-icon :size="13"><Clock /></el-icon> {{ scale.estimatedMinutes ?? "--" }} 分钟</span>
                <span class="footer-meta"><el-icon :size="13"><EditPen /></el-icon> {{ scale.questionCount ?? 0 }} 题</span>
              </div>
            </div>
            <el-icon class="card-arrow" :size="17"><ArrowRight /></el-icon>
          </div>
        </div>
      </div>
      <div v-if="total > 0" class="pagination-bar">
        <el-pagination
          v-model:current-page="pageNum"
          :page-size="pageSize"
          :total="total"
          :layout="PAGINATION_LAYOUT"
          background
          @current-change="handlePageChange"
        />
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { ArrowRight, Clock, EditPen, Search } from "@element-plus/icons-vue";
import { PAGINATION_LAYOUT } from "@/shared/api/config";
import { useScaleList } from "@/portal/composables/useScaleList";
import { iconFor, tintFor } from "./visual";

const router = useRouter();

const {
  activeCategory,
  keyword,
  scalesLoading,
  pageNum,
  pageSize,
  total,
  scaleList,
  categoryTabs,
  loadCategories,
  fetchScales,
  handleFilterChange,
  updateCategory,
  handleSearch,
  guardAndStart,
} = useScaleList();

const categoriesLoading = ref(false);
const categoriesError = ref(false);

async function handlePageChange() {
  await fetchScales();
}

async function safeLoadCategories() {
  categoriesLoading.value = true;
  categoriesError.value = false;
  try {
    await loadCategories();
  } catch {
    categoriesError.value = true;
  } finally {
    categoriesLoading.value = false;
  }
}

onMounted(() => {
  void safeLoadCategories();
  void handleFilterChange();
});
</script>

<style scoped>
.scale-list-page {
  max-width: 960px;
  margin: 0 auto;
  padding: 24px 20px 48px;
}

.page-header {
  margin-bottom: 24px;
}

.header-eyebrow {
  font-size: 12px;
  color: #6c63ff;
  font-weight: 600;
  letter-spacing: 0.5px;
  text-transform: uppercase;
}

.header-top-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.records-entry {
  font-size: 12px;
}

.page-title {
  font-size: 26px;
  font-weight: 700;
  color: #1a1a2e;
  margin: 6px 0;
}

.page-subtitle {
  font-size: 14px;
  color: #888;
  margin: 0 0 16px;
}

.search-row {
  display: flex;
  gap: 8px;
}

.search-input {
  flex: 1;
}

.category-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 20px;
  min-height: 34px;
}

.cat-skeleton {
  display: flex;
  gap: 8px;
}

.cat-sk {
  width: 80px;
  height: 28px;
  background: linear-gradient(90deg, #f0f0f5 25%, #e8e8f0 50%, #f0f0f5 75%);
  background-size: 200% 100%;
  animation: shimmer 1.4s infinite;
  border-radius: 999px;
}

.cat-tag {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 16px;
  border: 1px solid #ececf2;
  border-radius: 999px;
  background: #fff;
  font-size: 13px;
  color: #666;
  cursor: pointer;
  transition: all 0.2s;
  font-family: inherit;
}

.cat-tag.active {
  background: #6c63ff;
  color: #fff;
  border-color: #6c63ff;
}

.cat-count {
  font-size: 11px;
  background: rgba(108, 99, 255, 0.12);
  color: #6c63ff;
  border-radius: 999px;
  padding: 1px 8px;
}

.cat-tag.active .cat-count {
  background: rgba(255, 255, 255, 0.25);
  color: #fff;
}

.list-loading {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.sk-card {
  display: flex;
  background: #fff;
  border: 1px solid #ececf2;
  border-radius: 14px;
  overflow: hidden;
}

.sk-strip {
  display: block;
  width: 4px;
  background: linear-gradient(180deg, #f0f0f5, #e0e0ea);
}

.sk-content {
  flex: 1;
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sk-line {
  height: 14px;
  background: linear-gradient(90deg, #f0f0f5 25%, #e8e8f0 50%, #f0f0f5 75%);
  background-size: 200% 100%;
  animation: shimmer 1.4s infinite;
  border-radius: 6px;
}

.sk-lg {
  width: 40%;
}

.sk-sm {
  width: 60%;
}

@keyframes shimmer {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}

.empty-wrap {
  margin-top: 60px;
}

.scale-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.scale-card {
  background: #fff;
  border: 2px solid #ececf2;
  border-radius: 16px;
  overflow: hidden;
  cursor: pointer;
  transition: box-shadow 0.2s, transform 0.2s;
}

.scale-card:hover {
  box-shadow: 0 8px 24px rgba(26, 26, 46, 0.08);
  transform: translateY(-2px);
}

.scale-card.highlighted {
  border-color: #ffb74d;
  box-shadow: 0 0 0 4px rgba(255, 183, 77, 0.15);
}

.card-strip {
  display: block;
  height: 4px;
}

.card-body {
  display: flex;
  align-items: flex-start;
  gap: 15px;
  padding: 18px 20px;
}

.card-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  flex-shrink: 0;
}

.imoji {
  font-size: 22px;
  line-height: 1;
}

.badge-rec {
  position: absolute;
  top: -7px;
  right: -12px;
  font-size: 10px;
  background: #ffb74d;
  color: #fff;
  padding: 1px 6px;
  border-radius: 6px;
}

.card-content {
  flex: 1;
  min-width: 0;
}

.card-title {
  font-size: 15px;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0 0 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-desc {
  font-size: 12px;
  color: #888;
  margin: 0 0 10px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-footer {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.footer-meta {
  display: flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  color: #b0b6c4;
}

.rec-text {
  font-size: 11px;
  font-weight: 600;
  color: #f57f17;
  background: #fff8e1;
  border-radius: 4px;
  padding: 1px 6px;
}

.card-arrow {
  color: #ccc;
  flex-shrink: 0;
  margin-top: 4px;
}

.pagination-bar {
  margin-top: 24px;
  display: flex;
  justify-content: center;
}

@media (max-width: 768px) {
  .scale-grid {
    grid-template-columns: 1fr;
  }
}
</style>