<template>
  <div class="permission-page">
    <!-- ==================== 页头 ==================== -->
    <PageHeader title="权限管理" subtitle="管理系统权限项与分组归属，支持按角色查看绑定集合">
      <button type="button" class="op-icon-btn pl-refresh-btn" :disabled="loading" title="刷新" @click="refresh">
        <el-icon :size="16"><Refresh /></el-icon>
      </button>
      <el-button type="primary" @click="openDialog()">
        <el-icon class="btn-icon"><Plus /></el-icon>新增权限
      </el-button>
    </PageHeader>

    <!-- ==================== 统计卡（当前视图全量，不随筛选变化） ==================== -->
    <StatGrid :columns="4">
      <div class="stat-card">
        <div class="stat-icon stat-icon-indigo">
          <el-icon :size="22"><Key /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-num">{{ totalCount }}</div>
          <div class="stat-label">权限总数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-purple">
          <el-icon :size="22"><FolderOpened /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-num">{{ groupCount }}</div>
          <div class="stat-label">权限分组</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-green">
          <el-icon :size="22"><CircleCheck /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-num">{{ normalCount }}</div>
          <div class="stat-label">正常权限</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-gray">
          <el-icon :size="22"><CircleClose /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-num">{{ disabledCount }}</div>
          <div class="stat-label">停用权限</div>
        </div>
      </div>
    </StatGrid>

    <!-- ==================== 筛选卡 ==================== -->
    <div class="filter-card">
      <div class="toolbar">
        <!-- 视图范围：全部权限 / 按角色查看 -->
        <div class="seg-control">
          <button type="button" class="seg-control-item" :class="{ active: viewMode === 'all' }" @click="setViewMode('all')">
            全部权限
          </button>
          <button type="button" class="seg-control-item" :class="{ active: viewMode === 'role' }" @click="setViewMode('role')">
            按角色查看
          </button>
        </div>

        <!-- 全部视图：分组下拉；角色视图：角色下拉 -->
        <el-select v-if="viewMode === 'all'" v-model="groupFilter" placeholder="全部分组" style="width: 160px">
          <el-option label="全部分组" value="" />
          <el-option v-for="g in groupOptions" :key="g" :label="g" :value="g" />
        </el-select>
        <el-select
          v-else
          v-model="roleId"
          placeholder="请选择角色"
          :disabled="rolesLoading"
          :loading="rolesLoading"
          style="width: 190px"
        >
          <el-option v-for="r in roles" :key="r.id" :label="r.name" :value="r.id" />
        </el-select>

        <!-- 搜索 -->
        <el-input
          v-model="keyword"
          placeholder="搜索权限名称或标识（模糊匹配）..."
          clearable
          style="width: 260px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" @click="handleSearch">
          <el-icon class="btn-icon"><Search /></el-icon>搜索
        </el-button>
        <el-button @click="resetFilters">
          <el-icon class="btn-icon"><RefreshRight /></el-icon>重置
        </el-button>
        <el-button :disabled="!visibleTree.length" @click="toggleAllGroups">
          <el-icon class="btn-icon">
            <ArrowDown v-if="allGroupsCollapsed" />
            <ArrowUp v-else />
          </el-icon>
          {{ allGroupsCollapsed ? "展开全部" : "收起全部" }}
        </el-button>

        <div class="toolbar-spacer" />
        <span class="pl-count-text">
          <template v-if="viewMode === 'role' && currentRole">{{ currentRole.name }} · </template>共
          {{ totalCount }} 个权限
        </span>
      </div>
    </div>

    <!-- ==================== 分组折叠卡片（双列网格） ==================== -->
    <section v-loading="loading" class="pl-group-area">
      <div v-if="!loading && visibleTree.length" class="pl-group-grid">
        <div v-for="group in visibleTree" :key="group.groupName" class="pl-group-card">
          <!-- 组头：整行按钮，展开/收起 -->
          <button type="button" class="pl-group-head" @click="toggleGroup(group.groupName)">
            <span class="pl-group-icon" :style="{ background: hashColorFor(group.groupName).gradient }">
              <el-icon :size="16"><Key /></el-icon>
            </span>
            <span class="pl-group-meta">
              <span class="pl-group-name">{{ group.groupName }}</span>
              <span class="pl-group-sub">
                {{ group.permissions?.length ?? 0 }} 个权限 · {{ groupNormalCount(group) }} 正常<template
                  v-if="groupDisabledCount(group) > 0"
                >
                  · {{ groupDisabledCount(group) }} 停用</template
                >
              </span>
            </span>
            <el-icon class="pl-group-chevron" :class="{ collapsed: isCollapsed(group.groupName) }" :size="15">
              <ArrowDown />
            </el-icon>
          </button>

          <!-- 展开区：权限行（整行点击进详情） -->
          <ul v-show="!isCollapsed(group.groupName)" class="pl-perm-list">
            <li
              v-for="perm in group.permissions"
              :key="perm.id"
              class="pl-perm-row"
              title="点击查看详情"
              @click="openDetail(perm, group.groupName)"
            >
              <span class="pl-perm-icon" :style="{ background: hashColorFor(group.groupName).gradient }">
                <el-icon :size="12"><Key /></el-icon>
              </span>
              <span class="pl-perm-name">{{ perm.name }}</span>
              <code class="pl-perm-code">{{ perm.perms }}</code>
              <StatusBadge class="pl-perm-status" v-bind="permStatusBadge(perm.status)" />
              <span class="pl-perm-ops">
                <button type="button" class="op-icon-btn op-detail" title="详情" @click.stop="openDetail(perm, group.groupName)">
                  <el-icon :size="14"><View /></el-icon>
                </button>
                <button type="button" class="op-icon-btn op-edit" title="编辑" @click.stop="openDialog(perm, group.groupName)">
                  <el-icon :size="14"><Edit /></el-icon>
                </button>
                <button type="button" class="op-icon-btn op-del" title="删除" @click.stop="handleDelete(perm)">
                  <el-icon :size="14"><Delete /></el-icon>
                </button>
              </span>
            </li>
          </ul>
        </div>
      </div>

      <!-- 空态 -->
      <div v-else-if="!loading" class="pl-empty-state">{{ emptyText }}</div>
    </section>

    <!-- ==================== 图例 ==================== -->
    <div class="pl-legend">
      <el-icon :size="12"><User /></el-icon>
      <span>权限通过角色分配给用户/管理员；「全部权限」展示系统全部权限项，「按角色查看」展示该角色绑定的权限集合。</span>
    </div>

    <!-- ==================== 新增 / 编辑弹窗 ==================== -->
    <PermissionFormDialog
      v-model="dialogVisible"
      :target="formTarget"
      :group-name="formGroupName"
      :detail-remark="formRemark"
      :group-options="groupOptions"
      :submitting="submitting"
      @submit="handleSubmit"
    />

    <!-- ==================== 详情弹窗 ==================== -->
    <PermissionDetailDialog
      v-model="detailVisible"
      :target="detailTarget"
      :detail="detailData"
      :loading="detailLoading"
    />
  </div>
</template>

<script setup lang="ts">
import {
  ArrowDown,
  ArrowUp,
  CircleCheck,
  CircleClose,
  Delete,
  Edit,
  FolderOpened,
  Key,
  Plus,
  Refresh,
  RefreshRight,
  Search,
  User,
  View,
} from "@element-plus/icons-vue";
import { PageHeader, StatGrid, StatusBadge } from "@/admin/components";
import { hashColorFor } from "@/admin/composables/useHashColor";
import { usePermissionList } from "./usePermissionList";
import PermissionFormDialog from "./components/PermissionFormDialog.vue";
import PermissionDetailDialog from "./components/PermissionDetailDialog.vue";

const {
  // 列表 / 统计
  loading,
  totalCount,
  groupCount,
  normalCount,
  disabledCount,
  groupOptions,
  refresh,
  permStatusBadge,
  handleDelete,
  // 视图范围 / 筛选
  viewMode,
  roles,
  rolesLoading,
  roleId,
  currentRole,
  keyword,
  groupFilter,
  visibleTree,
  emptyText,
  setViewMode,
  handleSearch,
  resetFilters,
  // 分组折叠
  isCollapsed,
  toggleGroup,
  allGroupsCollapsed,
  toggleAllGroups,
  groupNormalCount,
  groupDisabledCount,
  // 新增 / 编辑
  dialogVisible,
  submitting,
  formTarget,
  formGroupName,
  formRemark,
  openDialog,
  handleSubmit,
  // 详情
  detailVisible,
  detailTarget,
  detailData,
  detailLoading,
  openDetail,
} = usePermissionList();
</script>

<style scoped>
/* 页面根：透明容器 + 20px 间距（页头间距由 gap 提供） */
.permission-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-height: 400px;
}

/* 页头刷新图标按钮（覆盖全局 .op-icon-btn 的透明底，带浅边框） */
.permission-page .pl-refresh-btn {
  padding: 7px;
  border: 1px solid var(--admin-border-strong);
  border-radius: 8px;
  background: #fff;
  color: var(--admin-text-secondary);
}
.permission-page .pl-refresh-btn:hover:not(:disabled) {
  background: var(--admin-primary-soft);
  border-color: #c7d2fe;
  color: var(--admin-primary);
}
.permission-page .pl-refresh-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

/* 分段控件内为原生 button，复位 UA 样式（选中态仍由全局 .seg-control-item.active 提供） */
.permission-page .seg-control-item {
  border: none;
  font-family: inherit;
  line-height: inherit;
}
.permission-page .seg-control-item:not(.active) {
  background: transparent;
}

/* 最右统计小字 */
.pl-count-text {
  font-size: 12px;
  color: var(--admin-text-muted);
  white-space: nowrap;
}

/* 分组区域（承载 loading 遮罩） */
.pl-group-area {
  min-height: 200px;
}

/* 双列网格（窄屏单列） */
.pl-group-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-items: start;
  gap: 16px;
}

@media (max-width: 1200px) {
  .pl-group-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

.pl-group-card {
  background: #fff;
  border: 1px solid var(--admin-border);
  border-radius: var(--admin-radius-card);
  box-shadow: var(--admin-shadow-card);
  overflow: hidden;
}

/* 组头 */
.pl-group-head {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 12px 16px;
  border: none;
  border-bottom: 1px solid var(--admin-border);
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition: background 0.18s ease;
}
.pl-group-head:hover {
  background: #f8fafc;
}
.pl-group-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}
.pl-group-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
}
.pl-group-name {
  font-size: 14px;
  font-weight: 600;
  color: #334155;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pl-group-sub {
  font-size: 11px;
  color: var(--admin-text-muted);
}
.pl-group-chevron {
  color: var(--admin-text-muted);
  flex-shrink: 0;
  transition: transform 0.2s ease;
}
.pl-group-chevron.collapsed {
  transform: rotate(-90deg);
}

/* 权限行 */
.pl-perm-list {
  list-style: none;
  margin: 0;
  padding: 4px 0;
}
.pl-perm-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 16px;
  cursor: pointer;
  transition: background 0.15s ease;
}
.pl-perm-row:hover {
  background: #f8fafc;
}
.pl-perm-row + .pl-perm-row {
  border-top: 1px solid #f1f5f9;
}
.pl-perm-icon {
  width: 26px;
  height: 26px;
  border-radius: 7px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}
.pl-perm-name {
  flex-shrink: 0;
  max-width: 9rem;
  font-size: 13px;
  font-weight: 500;
  color: #334155;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pl-perm-code {
  flex: 1;
  min-width: 0;
  padding: 2px 8px;
  border-radius: 6px;
  background: #f1f5f9;
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 11px;
  color: var(--admin-text-secondary);
  text-align: right;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pl-perm-status {
  flex-shrink: 0;
}
.pl-perm-ops {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}

/* 空态 */
.pl-empty-state {
  padding: 56px 0;
  border: 1px solid var(--admin-border);
  border-radius: var(--admin-radius-card);
  background: #fff;
  text-align: center;
  font-size: 13px;
  color: var(--admin-text-muted);
}

/* 图例 */
.pl-legend {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--admin-text-muted);
  line-height: 1.5;
}
</style>
