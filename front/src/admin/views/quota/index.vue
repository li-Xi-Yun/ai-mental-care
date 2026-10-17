<template>
  <div class="quota-config-page">
    <!-- ==================== 页头 ==================== -->
    <PageHeader title="全局限流管理" subtitle="配置 AI 文本、语音与形象资源的全局默认配额策略" />

    <!-- ==================== 统计卡 ==================== -->
    <section class="stat-grid">
      <div class="stat-card">
        <div class="stat-icon stat-icon-indigo">
          <el-icon :size="22"><Coin /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-num">{{ stats.total }}</div>
          <div class="stat-label">资源总数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-green">
          <el-icon :size="22"><CircleCheckFilled /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-num">{{ stats.enabledCount }}</div>
          <div class="stat-label">已启用限流</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-red">
          <el-icon :size="22"><CircleCloseFilled /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-num">{{ stats.disabledCount }}</div>
          <div class="stat-label">已停用资源</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-gray">
          <el-icon :size="22"><DataLine /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-num">{{ stats.sumLimit }}</div>
          <div class="stat-label">默认总额度</div>
        </div>
      </div>
    </section>

    <!-- ==================== 筛选卡 ==================== -->
    <section class="filter-card">
      <div class="toolbar">
        <el-select v-model="filterState.resourceType" placeholder="资源类型" clearable style="width: 150px" @change="handleSearch">
          <el-option v-for="r in QUOTA_RESOURCE_OPTIONS" :key="r.value" :label="r.label" :value="r.value" />
        </el-select>
        <el-select v-model="filterState.available" placeholder="可用状态" clearable style="width: 130px" @change="handleSearch">
          <el-option v-for="a in QUOTA_AVAILABLE_OPTIONS" :key="a.value" :label="a.label" :value="a.value" />
        </el-select>
        <el-select v-model="filterState.enabled" placeholder="限流状态" clearable style="width: 130px" @change="handleSearch">
          <el-option v-for="e in QUOTA_ENABLED_OPTIONS" :key="e.value" :label="e.label" :value="e.value" />
        </el-select>
        <el-button type="primary" @click="handleSearch">
          <el-icon class="btn-icon"><Search /></el-icon>查询
        </el-button>
        <el-button @click="handleReset">
          <el-icon class="btn-icon"><RefreshRight /></el-icon>重置
        </el-button>
      </div>
    </section>

    <!-- ==================== 表格卡片 ==================== -->
    <section class="content-card">
      <el-table v-loading="loading" :data="filteredList" row-key="resourceType" class="quota-config-table">
        <el-table-column label="资源类型" min-width="180">
          <template #default="{ row }">
            <span class="resource-badge" :style="resourceBadgeStyle((row as QuotaConfigVO).resourceType)">
              <span class="resource-dot" :style="resourceDotStyle((row as QuotaConfigVO).resourceType)" />
              {{ resourceNameOf(row as QuotaConfigVO) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="默认上限（次）" width="140" align="center">
          <template #default="{ row }">
            <span class="limit-text">{{ row.defaultLimit ?? "—" }}</span>
          </template>
        </el-table-column>
        <el-table-column label="资源可用" width="110" align="center">
          <template #default="{ row }">
            <span class="status-badge" :style="availableBadgeStyle((row as QuotaConfigVO).available)">
              <span class="status-dot" :style="availableDotStyle((row as QuotaConfigVO).available)" />
              {{ availableLabel((row as QuotaConfigVO).available) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="限流状态" width="110" align="center">
          <template #default="{ row }">
            <span class="status-badge" :style="enabledBadgeStyle((row as QuotaConfigVO).enabled)">
              <span class="status-dot" :style="enabledDotStyle((row as QuotaConfigVO).enabled)" />
              {{ enabledLabel((row as QuotaConfigVO).enabled) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="计量周期" width="110" align="center">
          <template #default="{ row }">
            <span class="cycle-text">{{ cycleLabel((row as QuotaConfigVO).cycleType) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="说明" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.remark" class="remark-text">{{ row.remark }}</span>
            <span v-else class="remark-empty">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <div class="op-cell">
              <el-button text size="small" class="op-btn op-edit" @click="openEdit(row as QuotaConfigVO)">编辑</el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无配额配置数据" :image-size="80" />
        </template>
      </el-table>
    </section>

    <!-- ==================== 配置编辑弹窗 ==================== -->
    <QuotaConfigFormDialog
      v-model="dialogVisible"
      :resource-name="resourceNameOf(form)"
      :submitting="submitting"
      :form="form"
      @submit="handleSubmit"
    />
  </div>
</template>

<script setup lang="ts">
import PageHeader from "@/admin/components/PageHeader.vue";
import { useQuotaList } from "./useQuotaList";
import QuotaConfigFormDialog from "./components/QuotaConfigFormDialog.vue";
import type { QuotaConfigVO } from "@/admin/api/quota/quota";

const {
  // 展示工具
  resourceNameOf,
  resourceBadgeStyle,
  resourceDotStyle,
  availableLabel,
  availableBadgeStyle,
  availableDotStyle,
  enabledLabel,
  enabledBadgeStyle,
  enabledDotStyle,
  cycleLabel,
  // 列表与筛选
  loading,
  filteredList,
  filterState,
  handleSearch,
  handleReset,
  // 统计
  stats,
  // 编辑弹窗
  dialogVisible,
  submitting,
  form,
  openEdit,
  handleSubmit,
  // 常量
  QUOTA_RESOURCE_OPTIONS,
  QUOTA_AVAILABLE_OPTIONS,
  QUOTA_ENABLED_OPTIONS,
} = useQuotaList();
</script>

<style scoped>
.quota-config-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
  color: #1e293b;
}

/* 页面特有样式（公共 stat-grid / stat-card / filter-card / toolbar / status-badge / op-cell 已迁至 styles/index.css） */

.resource-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 10px;
  border-radius: 999px;
  border: 1px solid;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}

.resource-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.limit-text {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  font-variant-numeric: tabular-nums;
}

.cycle-text {
  font-size: 12px;
  color: #64748b;
}

.remark-text {
  font-size: 12px;
  color: #475569;
}

.remark-empty {
  color: #cbd5e1;
}
</style>
