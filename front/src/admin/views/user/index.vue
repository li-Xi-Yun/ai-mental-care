<template>
  <div class="user-manage-page">
    <!-- ==================== 统计条 ==================== -->
    <section class="stat-grid">
      <div class="stat-card">
        <div class="stat-icon stat-icon-indigo">
          <el-icon :size="22"><UserFilled /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-num">{{ stats.total }}</div>
          <div class="stat-label">用户总数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-green">
          <el-icon :size="22"><CircleCheckFilled /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-num">{{ stats.normal }}</div>
          <div class="stat-label">正常</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-red">
          <el-icon :size="22"><CircleCloseFilled /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-num">{{ stats.ban }}</div>
          <div class="stat-label">封禁中</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-gray">
          <el-icon :size="22"><RemoveFilled /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-num">{{ stats.logout }}</div>
          <div class="stat-label">已注销</div>
        </div>
      </div>
    </section>

    <!-- ==================== 主卡片 ==================== -->
    <section class="content-card">
      <!-- 搜索条 -->
      <div class="toolbar">
        <el-input
          v-model="searchForm.keyword"
          placeholder="搜索用户名 / 登录账号 / 手机号 / 邮箱"
          clearable
          style="width: 260px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-select
          v-model="searchForm.status"
          placeholder="全部状态"
          clearable
          style="width: 130px"
          @change="handleSearch"
        >
          <el-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
        <el-button type="primary" @click="handleSearch">
          <el-icon class="btn-icon"><Search /></el-icon>查询
        </el-button>
        <el-button @click="handleReset">
          <el-icon class="btn-icon"><RefreshRight /></el-icon>重置
        </el-button>
        <div class="toolbar-spacer" />
        <el-button type="danger" plain :disabled="!selectedRows.length" @click="openBatchBan">
          <el-icon class="btn-icon"><CircleCloseFilled /></el-icon>批量封禁{{ selectedRows.length ? `（${selectedRows.length}）` : "" }}
        </el-button>
      </div>

      <!-- 表格 -->
      <el-table
        v-loading="loading"
        :data="tableData"
        row-key="id"
        class="user-table"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="46" />
        <el-table-column prop="id" label="用户ID" width="92" />
        <el-table-column label="用户名" min-width="170">
          <template #default="{ row }">
            <div class="user-cell">
              <div class="avatar-wrap">
                <img v-if="row.avatar" :src="row.avatar" class="avatar-img" alt="" />
                <span v-else class="avatar-letter" :style="avatarStyle(row.gender)">{{ avatarText(row) }}</span>
              </div>
              <div class="user-info">
                <div class="user-name">{{ row.username || "—" }}</div>
                <div class="user-account">{{ row.loginAccount || "" }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="手机号" width="130">
          <template #default="{ row }">
            <span class="mobile-text">{{ mobileOf(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="性别" width="86">
          <template #default="{ row }">
            <span class="gender-tag" :style="genderTagStyle(row.gender)">
              <span class="gender-dot" :style="{ background: genderMeta(row.gender).color }" />
              {{ genderMeta(row.gender).label }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="注册时间" width="160">
          <template #default="{ row }">
            <span class="time-text">{{ formatTime(row.createdTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <span class="status-badge" :style="statusBadgeStyle(row.status)">
              <span class="status-dot" :style="{ background: STATUS_META[row.status]?.color ?? '#94a3b8' }" />
              {{ statusLabel(row.status) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <div class="op-cell">
              <el-button type="primary" text size="small" @click="openDetail(row)">详情</el-button>
              <el-button type="primary" text size="small" :disabled="row.status === 3" @click="openAssignDialog(row)">
                编辑角色
              </el-button>
              <el-button
                v-if="row.status === 2"
                type="success"
                text
                size="small"
                @click="handleEnable(row)"
              >
                启用
              </el-button>
              <el-button
                v-else
                type="danger"
                text
                size="small"
                :disabled="row.status === 3"
                @click="openBanDialog(row)"
              >
                封禁
              </el-button>
              <el-button type="warning" text size="small" @click="openPermDrawer(row)">
                临时权限
              </el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无用户数据" :image-size="80" />
        </template>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-bar">
        <span class="page-total">共 {{ total }} 条</span>
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="total"
          :layout="PAGINATION_LAYOUT"
          background
          @current-change="loadData"
        />
      </div>
    </section>

    <!-- ==================== 用户详情抽屉 ==================== -->
    <el-drawer v-model="detailVisible" :title="`用户详情 · ${detailTarget?.username ?? ''}`" size="540px">
      <div v-loading="detailLoading" class="drawer-body">
        <template v-if="detail">
          <!-- 顶部资料 -->
          <div class="detail-head">
            <div class="avatar-wrap avatar-lg">
              <img v-if="detail.avatar" :src="detail.avatar" class="avatar-img" alt="" />
              <span v-else class="avatar-letter" :style="avatarStyle(detail.gender)">{{ avatarLargeText(detail) }}</span>
            </div>
            <div class="detail-head-info">
              <div class="detail-name">{{ detail.username || "—" }}</div>
              <div class="detail-account">{{ detail.loginAccount || "" }}</div>
              <span class="status-badge" :style="statusBadgeStyle(detail.status ?? 3)">
                <span class="status-dot" :style="{ background: STATUS_META[detail.status ?? 3]?.color ?? '#94a3b8' }" />
                {{ statusLabel(detail.status) }}
              </span>
            </div>
          </div>

          <!-- 基本资料 -->
          <el-descriptions :column="1" border size="small" class="detail-desc">
            <el-descriptions-item label="用户ID">{{ detail.id }} </el-descriptions-item>
            <el-descriptions-item label="登录账号">{{ detail.loginAccount || "—" }}</el-descriptions-item>
            <el-descriptions-item label="手机号">
              <span class="desc-highlight">{{ detail.mobile || "—" }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="邮箱">{{ detail.email || "—" }}</el-descriptions-item>
            <el-descriptions-item label="性别">{{ genderMeta(detail.gender).label }}</el-descriptions-item>
            <el-descriptions-item label="注册时间">{{ formatTime(detail.createdTime) }}</el-descriptions-item>
            <el-descriptions-item label="最后更新">{{ formatTime(detail.updatedTime) }}</el-descriptions-item>
            <el-descriptions-item label="个人简介">
              <span class="desc-intro">{{ detail.introduction || "—" }}</span>
            </el-descriptions-item>
          </el-descriptions>

          <!-- 封禁记录 -->
          <div class="detail-section">
            <div class="detail-section-title">
              <el-icon :size="15"><WarningFilled /></el-icon>
              封禁记录
            </div>
            <div v-if="detail.status === 2" class="ban-card">
              <div class="ban-item">
                <span class="ban-label">封禁理由</span>
                <span class="ban-value">{{ detail.banReason || "—" }}</span>
              </div>
              <div class="ban-item">
                <span class="ban-label">封禁时间</span>
                <span class="ban-value">{{ formatTime(detail.banTime) }}</span>
              </div>
              <div class="ban-item">
                <span class="ban-label">解封时间</span>
                <span class="ban-value ban-permanent">{{ detail.banEndTime ? formatTime(detail.banEndTime) : "永久封禁" }}</span>
              </div>
            </div>
            <div v-else class="ban-empty">
              <el-icon :size="16"><CircleCheckFilled /></el-icon>
              <span>该账号当前未被封禁</span>
            </div>
          </div>
        </template>
      </div>
    </el-drawer>

    <!-- ==================== 角色分配弹窗 ==================== -->
    <el-dialog
      v-model="assignVisible"
      :title="`编辑角色：${assignTarget?.username ?? ''}`"
      width="480px"
      align-center
      :close-on-click-modal="false"
    >
      <el-form label-position="top">
        <el-form-item label="角色">
          <el-select
            v-model="selectedRoleIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="请选择角色"
            style="width: 100%"
            :loading="roleLoading"
          >
            <el-option v-for="r in roleOptions" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
          <div class="form-tip">保存后将覆盖该用户原有的全部角色</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">取 消</el-button>
        <el-button type="primary" :loading="assignSubmitting" @click="handleAssignSubmit">确 定</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 封禁弹窗 ==================== -->
    <el-dialog
      v-model="banVisible"
      :title="banTitle"
      width="480px"
      align-center
      :close-on-click-modal="false"
      @closed="banFormRef?.clearValidate()"
    >
      <el-form ref="banFormRef" :model="banForm" :rules="banRules" label-position="top">
        <el-form-item label="封禁截止时间">
          <el-date-picker
            v-model="banForm.banEndTime"
            type="datetime"
            placeholder="留空表示永久封禁"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
            :disabled-date="disablePastDate"
          />
          <div class="form-tip">不选择时间则为永久封禁；到期后将自动恢复正常状态。</div>
        </el-form-item>
        <el-form-item label="封禁理由" prop="banReason">
          <el-input
            v-model="banForm.banReason"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="请输入封禁理由"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="banVisible = false">取 消</el-button>
        <el-button type="danger" :loading="banSubmitting" @click="handleBanSubmit">确认封禁</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 临时权限抽屉 ==================== -->
    <el-drawer v-model="permVisible" :title="`临时权限 · ${permTarget?.username ?? ''}`" size="560px">
      <div v-loading="permLoading" class="drawer-body">
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import {
  CircleCheckFilled,
  CircleCloseFilled,
  RefreshRight,
  RemoveFilled,
  Search,
  UserFilled,
  WarningFilled,
} from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import type { FormInstance, FormRules } from "element-plus";
import dayjs from "@/shared/utils/dayjs";
import type { AxiosResponse } from "axios";
import type { ListResult } from "@shared/api/types";
import { DEFAULT_PAGE_SIZE, PAGINATION_LAYOUT } from "@/shared/api/config";
import {
  getUserPage,
  getUserDetail,
  updateUserStatus,
  getUserRoles,
  assignUserRoles,
  getUserTempPermissions,
} from "@/admin/api/admin/user-manage";
import type {
  SysUserPageVO,
  SysUserDetailVO,
  SysUserStatusUpdateDTO,
  SimpleRole,
  TempPermissionVO,
} from "@/admin/api/admin/user-manage";
import { getAllSimpleRoles } from "@/admin/api/permission/role";

/* ==================== 状态与性别元数据 ==================== */

const STATUS_META: Record<number, { label: string; color: string; bg: string; border: string }> = {
  0: { label: "正常", color: "#10b981", bg: "#ecfdf5", border: "#a7f3d0" },
  1: { label: "异常", color: "#f59e0b", bg: "#fffbeb", border: "#fde68a" },
  2: { label: "封禁", color: "#ef4444", bg: "#fef2f2", border: "#fecaca" },
  3: { label: "注销", color: "#94a3b8", bg: "#f1f5f9", border: "#e2e8f0" },
};

const STATUS_OPTIONS = [0, 1, 2, 3].map((v) => ({ value: v, label: STATUS_META[v].label }));

/** 性别：0=女，1=男，2=未知 */
const GENDER_META: Record<number, { label: string; color: string; bg: string; border: string; grad: string }> = {
  0: { label: "女", color: "#db2777", bg: "#fdf2f8", border: "#fbcfe8", grad: "linear-gradient(135deg,#ec4899,#f472b6)" },
  1: { label: "男", color: "#2563eb", bg: "#eff6ff", border: "#bfdbfe", grad: "linear-gradient(135deg,#2563eb,#60a5fa)" },
  2: { label: "未知", color: "#64748b", bg: "#f1f5f9", border: "#e2e8f0", grad: "linear-gradient(135deg,#94a3b8,#cbd5e1)" },
};

const PERM_STATUS_TEXT: Record<number, string> = { 0: "有效", 1: "已作废", 2: "已过期" };

/* ==================== 工具函数 ==================== */

function genderMeta(g?: number) {
  return g === undefined || g === null ? GENDER_META[2] : (GENDER_META[g] ?? GENDER_META[2]);
}

function genderTagStyle(g?: number) {
  const meta = genderMeta(g);
  return { color: meta.color, background: meta.bg, borderColor: meta.border };
}

function avatarStyle(g?: number): Record<string, string> {
  return { background: genderMeta(g).grad };
}

function statusLabel(status?: number): string {
  return status === undefined || status === null ? "未知" : (STATUS_META[status]?.label ?? "未知");
}

function statusBadgeStyle(status?: number): Record<string, string> {
  const meta = STATUS_META[status ?? 3] ?? STATUS_META[3];
  return { color: meta.color, background: meta.bg, borderColor: meta.border };
}

function formatTime(v?: string): string {
  return v ? dayjs(v).format("YYYY-MM-DD HH:mm") : "—";
}

function avatarText(row: any): string {
  const name = row.username?.trim();
  const account = row.loginAccount?.trim();
  return (name || account || "?").charAt(0).toUpperCase();
}

function avatarLargeText(detail: SysUserDetailVO): string {
  const name = detail.username?.trim();
  const account = detail.loginAccount?.trim();
  return (name || account || "?").charAt(0).toUpperCase();
}

/** 临时权限 perms 字段按逗号拆分为权限码数组 */
function permCodes(item: TempPermissionVO): string[] {
  return item.perms ? item.perms.split(",").map((s) => s.trim()).filter(Boolean) : [];
}

function permStatusText(status?: number): string {
  return status === undefined || status === null ? "未知" : (PERM_STATUS_TEXT[status] ?? "未知");
}

function permTagType(status?: number): "success" | "danger" | "info" {
  const t: Record<number, "success" | "danger" | "info"> = { 0: "success", 1: "danger", 2: "info" };
  return t[status ?? 0] ?? "info";
}

function permTimelineType(status?: number): "primary" | "danger" | "info" {
  const t: Record<number, "primary" | "danger" | "info"> = { 0: "primary", 1: "danger", 2: "info" };
  return t[status ?? 0] ?? "info";
}

const disablePastDate = (d: Date) => d.getTime() < Date.now();

/* ==================== 列表页状态 ==================== */

const loading = ref(false);
const tableData = ref<SysUserPageVO[]>([]);
const total = ref(0);
const currentPage = ref(1);
const pageSize = ref(DEFAULT_PAGE_SIZE);

const searchForm = reactive<{ keyword: string; status?: number }>({ keyword: "", status: undefined });

const stats = reactive({ total: 0, normal: 0, ban: 0, logout: 0 });

/** 列表行补充详情（用于手机号等列表接口未返回的字段） */
const detailMap = ref<Record<number, SysUserDetailVO>>({});

const selectedRows = ref<SysUserPageVO[]>([]);

function handleSelectionChange(rows: any[]) {
  selectedRows.value = rows;
}

function mobileOf(row: any): string {
  return detailMap.value[row.id]?.mobile || "—";
}

/* ==================== 数据加载 ==================== */

async function loadData() {
  loading.value = true;
  try {
    const res = await getUserPage({
      pageNum: currentPage.value,
      pageSize: pageSize.value,
      keyword: searchForm.keyword.trim() || undefined,
      status: (searchForm.status ?? "") === "" ? undefined : searchForm.status,
    });
    tableData.value = res.data.data?.records ?? [];
    total.value = res.data.data?.total ?? 0;
  } catch {
    ElMessage.error("加载用户列表失败，请稍后重试");
  } finally {
    loading.value = false;
  }
  await loadDetails();
}

/** 逐行拉取详情以补齐手机号等字段（单个失败不影响整体） */
async function loadDetails() {
  const map: Record<number, SysUserDetailVO> = {};
  await Promise.all(
    tableData.value.map(async (row) => {
      try {
        const res = await getUserDetail(row.id);
        if (res.data?.data) map[row.id] = res.data.data;
      } catch {
        // 忽略单行详情加载失败
      }
    }),
  );
  detailMap.value = map;
}

async function loadStats() {
  const tasks = [
    getUserPage({ pageNum: 1, pageSize: 1 }),
    getUserPage({ pageNum: 1, pageSize: 1, status: 0 }),
    getUserPage({ pageNum: 1, pageSize: 1, status: 2 }),
    getUserPage({ pageNum: 1, pageSize: 1, status: 3 }),
  ];
  const [totalRes, normalRes, banRes, logoutRes] = await Promise.allSettled(tasks);
  const extract = (r: PromiseSettledResult<AxiosResponse<ListResult<SysUserPageVO>>>) =>
    r.status === "fulfilled" ? (r.value.data.data?.total ?? 0) : 0;
  stats.total = extract(totalRes);
  stats.normal = extract(normalRes);
  stats.ban = extract(banRes);
  stats.logout = extract(logoutRes);
}

async function refreshAll() {
  await Promise.all([loadData(), loadStats()]);
}

/* ==================== 搜索 / 重置 ==================== */

function handleSearch() {
  currentPage.value = 1;
  loadData();
}

function handleReset() {
  searchForm.keyword = "";
  searchForm.status = undefined;
  currentPage.value = 1;
  loadData();
}

/* ==================== 封禁 / 启用 ==================== */

const banVisible = ref(false);
const banTitle = ref("封禁账号");
const banSubmitting = ref(false);
const banFormRef = ref<FormInstance>();
const banForm = reactive({ banReason: "", banEndTime: "" });
const banTarget = ref<SysUserPageVO | null>(null);
const batchTargets = ref<SysUserPageVO[]>([]);

const banRules: FormRules = {
  banReason: [{ required: true, message: "请输入封禁理由", trigger: "blur" }],
};

function openBanDialog(row: any) {
  banTarget.value = row;
  batchTargets.value = [];
  banTitle.value = `封禁账号：${row.username ?? row.loginAccount ?? ""}`;
  banForm.banReason = "";
  banForm.banEndTime = "";
  banVisible.value = true;
}

function openBatchBan() {
  if (!selectedRows.value.length) return;
  banTarget.value = null;
  batchTargets.value = [...selectedRows.value];
  banTitle.value = `批量封禁（${batchTargets.value.length} 个用户）`;
  banForm.banReason = "";
  banForm.banEndTime = "";
  banVisible.value = true;
}

async function handleBanSubmit() {
  const reason = banForm.banReason.trim();
  if (!reason) {
    ElMessage.warning("请输入封禁理由");
    return;
  }
  const targets = batchTargets.value.length ? batchTargets.value : banTarget.value ? [banTarget.value] : [];
  if (!targets.length) return;
  banSubmitting.value = true;
  try {
    const payload: SysUserStatusUpdateDTO = {
      status: 2,
      banReason: reason,
      banEndTime: banForm.banEndTime || undefined,
    };
    await Promise.all(targets.map((t) => updateUserStatus(t.id, payload)));
    ElMessage.success(batchTargets.value.length ? "所选用户已封禁" : "用户已封禁");
    banVisible.value = false;
    await refreshAll();
  } catch {
    ElMessage.error("封禁失败，请稍后重试");
  } finally {
    banSubmitting.value = false;
  }
}

async function handleEnable(row: any) {
  try {
    await ElMessageBox.confirm(`确定启用账号「${row.username ?? row.loginAccount}」吗？`, "启用确认", {
      type: "warning",
      confirmButtonText: "启用",
      cancelButtonText: "取消",
    });
  } catch {
    return;
  }
  try {
    await updateUserStatus(row.id, { status: 0 });
    ElMessage.success("账号已启用");
    await refreshAll();
  } catch {
    ElMessage.error("启用失败，请稍后重试");
  }
}

/* ==================== 角色分配 ==================== */

const assignVisible = ref(false);
const assignSubmitting = ref(false);
const assignTarget = ref<SysUserPageVO | null>(null);
const roleLoading = ref(false);
const roleOptions = ref<SimpleRole[]>([]);
const selectedRoleIds = ref<number[]>([]);

async function openAssignDialog(row: any) {
  assignTarget.value = row;
  assignVisible.value = true;
  roleLoading.value = true;
  selectedRoleIds.value = [];
  try {
    const [optsRes, curRes] = await Promise.all([getAllSimpleRoles(), getUserRoles(row.id)]);
    roleOptions.value = (optsRes.data?.data ?? []) as SimpleRole[];
    selectedRoleIds.value = (curRes.data?.data ?? []).map((r) => r.id);
  } catch {
    ElMessage.error("加载角色数据失败，请稍后重试");
  } finally {
    roleLoading.value = false;
  }
}

async function handleAssignSubmit() {
  if (!assignTarget.value) return;
  assignSubmitting.value = true;
  try {
    await assignUserRoles(assignTarget.value.id, selectedRoleIds.value);
    ElMessage.success("角色分配成功");
    assignVisible.value = false;
  } catch {
    ElMessage.error("角色分配失败，请稍后重试");
  } finally {
    assignSubmitting.value = false;
  }
}

/* ==================== 用户详情抽屉 ==================== */

const detailVisible = ref(false);
const detailLoading = ref(false);
const detailTarget = ref<SysUserPageVO | null>(null);
const detail = ref<SysUserDetailVO | null>(null);

async function openDetail(row: any) {
  detailTarget.value = row;
  detail.value = null;
  detailVisible.value = true;
  detailLoading.value = true;
  try {
    const res = await getUserDetail(row.id);
    detail.value = res.data?.data ?? null;
  } catch {
    ElMessage.error("加载用户详情失败，请稍后重试");
  } finally {
    detailLoading.value = false;
  }
}

/* ==================== 临时权限抽屉 ==================== */

const permVisible = ref(false);
const permLoading = ref(false);
const permTarget = ref<SysUserPageVO | null>(null);
const permList = ref<TempPermissionVO[]>([]);

async function openPermDrawer(row: any) {
  permTarget.value = row;
  permList.value = [];
  permVisible.value = true;
  permLoading.value = true;
  try {
    const res = await getUserTempPermissions(row.id);
    permList.value = res.data?.data ?? [];
  } catch {
    ElMessage.error("加载临时权限失败，请稍后重试");
  } finally {
    permLoading.value = false;
  }
}

/* ==================== 初始化 ==================== */

onMounted(() => {
  refreshAll();
});
</script>

<style scoped>
.user-manage-page {
  --el-color-primary: #6366f1;
  --el-color-primary-light-3: #818cf8;
  --el-color-primary-light-5: #a5b4fc;
  --el-color-primary-light-7: #c7d2fe;
  --el-color-primary-light-8: #e0e7ff;
  --el-color-primary-light-9: #eef2ff;
  --el-color-primary-dark-2: #4338ca;
  --el-border-radius-base: 8px;

  display: flex;
  flex-direction: column;
  gap: 16px;
  color: #1e293b;
}

/* ==================== 统计条 ==================== */

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  background: #fff;
  border: 1px solid #eef1f6;
  border-radius: 14px;
  padding: 18px 20px;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.04);
  transition: box-shadow 0.2s ease, transform 0.2s ease;
}

.stat-card:hover {
  box-shadow: 0 6px 18px rgba(16, 24, 40, 0.08);
  transform: translateY(-1px);
}

.stat-icon {
  width: 46px;
  height: 46px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}

.stat-icon-indigo {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
}

.stat-icon-green {
  background: linear-gradient(135deg, #10b981, #059669);
  box-shadow: 0 4px 12px rgba(16, 185, 129, 0.3);
}

.stat-icon-red {
  background: linear-gradient(135deg, #ef4444, #f97316);
  box-shadow: 0 4px 12px rgba(239, 68, 68, 0.3);
}

.stat-icon-gray {
  background: linear-gradient(135deg, #64748b, #94a3b8);
  box-shadow: 0 4px 12px rgba(100, 116, 139, 0.3);
}

.stat-num {
  font-size: 26px;
  font-weight: 700;
  line-height: 1.1;
  color: #0f172a;
  font-variant-numeric: tabular-nums;
}

.stat-label {
  margin-top: 2px;
  font-size: 12px;
  color: #64748b;
}

/* ==================== 主卡片 ==================== */

.content-card {
  background: #fff;
  border: 1px solid #eef1f6;
  border-radius: 14px;
  padding: 20px;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.04);
}

.toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 18px;
}

.toolbar-spacer {
  flex: 1;
}

.btn-icon {
  margin-right: 4px;
}

/* ==================== 表格 ==================== */

.user-table {
  --el-table-header-bg-color: #f8fafc;
  --el-table-header-text-color: #475569;
  --el-table-border-color: #eef1f6;
  --el-table-row-hover-bg-color: #f8faff;
  width: 100%;
  border-radius: 10px;
  overflow: hidden;
}

.user-table :deep(.el-table__header th) {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.3px;
}

.user-table :deep(.el-table__cell) {
  padding: 10px 0;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.avatar-wrap {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  position: relative;
}

.avatar-wrap.avatar-lg {
  width: 64px;
  height: 64px;
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.avatar-letter {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.5px;
  user-select: none;
}

.user-info {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.user-name {
  font-weight: 600;
  color: #0f172a;
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-account {
  font-size: 11.5px;
  color: #94a3b8;
  margin-top: 1px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.mobile-text {
  font-size: 12.5px;
  color: #475569;
  font-variant-numeric: tabular-nums;
}

.gender-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 10px;
  border-radius: 999px;
  border: 1px solid;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}

.gender-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.status-badge {
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

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.time-text {
  font-size: 12px;
  color: #94a3b8;
  font-variant-numeric: tabular-nums;
}

.op-cell {
  display: flex;
  align-items: center;
  gap: 0;
  white-space: nowrap;
}

.op-cell :deep(.el-button + .el-button) {
  margin-left: 0;
}

/* ==================== 分页 ==================== */

.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
}

.page-total {
  font-size: 12px;
  color: #94a3b8;
}

/* ==================== 抽屉通用 ==================== */

.drawer-body {
  min-height: 200px;
}

.detail-head {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 4px 0 18px;
}

.detail-head-info {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}

.detail-name {
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
}

.detail-account {
  font-size: 12.5px;
  color: #94a3b8;
}

.detail-desc {
  margin-bottom: 20px;
}

.detail-desc :deep(.el-descriptions__label) {
  color: #64748b;
  font-weight: 500;
  width: 96px;
}

.desc-highlight {
  font-weight: 600;
  color: #334155;
  font-variant-numeric: tabular-nums;
}

.desc-intro {
  color: #475569;
  line-height: 1.6;
  white-space: pre-wrap;
}

.detail-section {
  margin-top: 6px;
}

.detail-section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 10px;
}

.detail-section-title .el-icon {
  color: #ef4444;
}

.ban-card {
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 10px;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ban-item {
  display: flex;
  gap: 10px;
  font-size: 13px;
  line-height: 1.6;
}

.ban-label {
  color: #9f1239;
  font-weight: 500;
  flex-shrink: 0;
  width: 60px;
}

.ban-value {
  color: #7f1d1d;
  word-break: break-all;
}

.ban-permanent {
  font-weight: 600;
}

.ban-empty {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #94a3b8;
  font-size: 13px;
  padding: 12px 14px;
  background: #f8fafc;
  border: 1px dashed #e2e8f0;
  border-radius: 10px;
}

.ban-empty .el-icon {
  color: #22c55e;
}

/* ==================== 表单提示 ==================== */

.form-tip {
  margin-top: 6px;
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.6;
}

/* ==================== 临时权限时间线 ==================== */

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