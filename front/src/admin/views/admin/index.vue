<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { Search, Plus, RefreshRight } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import type { FormInstance, FormRules } from "element-plus";
import dayjs from "dayjs";
import type { AxiosResponse } from "axios";
import { DEFAULT_PAGE_SIZE, PAGINATION_LAYOUT } from "@/shared/api/config";
import { useAdminStore } from "@/admin/stores/admin";
import {
  getAdminPage,
  addAdmin,
  updateAdmin,
  updateAdminStatus,
  resetAdminPwd,
  deleteAdmin,
  getAdminRoles,
  assignAdminRoles,
  getAllSimpleRoles,
} from "@/admin/api/admin/admin-manage";
import type {
  AdminAddDTO,
  AdminPageVO,
  AdminUpdateDTO,
  AdminStatusUpdateDTO,
  RoleVO,
  SimpleRole,
} from "@/admin/api/admin/admin-manage";
import type { ListResult } from "@shared/api/types";

/* ==================== 状态元数据 ==================== */

const STATUS_META: Record<number, { label: string; color: string; bg: string; border: string }> = {
  0: { label: "正常", color: "#10b981", bg: "#ecfdf5", border: "#a7f3d0" },
  1: { label: "异常", color: "#f59e0b", bg: "#fffbeb", border: "#fde68a" },
  2: { label: "封禁", color: "#ef4444", bg: "#fef2f2", border: "#fecaca" },
  3: { label: "注销", color: "#94a3b8", bg: "#f1f5f9", border: "#e2e8f0" },
};

const STATUS_OPTIONS = [0, 1, 2, 3].map((v) => ({ value: v, label: STATUS_META[v].label }));

function statusBadgeStyle(status: number): Record<string, string> {
  const meta = STATUS_META[status] ?? STATUS_META[3];
  return { color: meta.color, background: meta.bg, borderColor: meta.border };
}

function statusLabel(status: number): string {
  return STATUS_META[status]?.label ?? "未知";
}

function roleStyle(role: RoleVO): Record<string, string> {
  const colors = [
    { color: "#6366f1", bg: "#eef2ff", border: "#e0e7ff" },
    { color: "#0ea5e9", bg: "#eff6ff", border: "#e0f2fe" },
    { color: "#10b981", bg: "#ecfdf5", border: "#a7f3d0" },
  ];
  const c = colors[(role.id ?? 0) % colors.length];
  return { color: c.color, background: c.bg, borderColor: c.border };
}

function formatTime(v?: string): string {
  return v ? dayjs(v).format("YYYY-MM-DD HH:mm") : "—";
}

/** 生成一个满足后端正则（大小写字母+数字，6-20 位）的占位密码，仅在「使用默认密码」时用于通过服务端校验 */
function generateValidPassword(): string {
  const chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
  const rand = (n: number) =>
    Array.from({ length: n }, () => chars[Math.floor(Math.random() * chars.length)]).join("");
  return `A${rand(3)}a${rand(3)}1${rand(5)}`;
}

/* ==================== 页面状态 ==================== */

const loading = ref(false);
const tableData = ref<AdminPageVO[]>([]);
const total = ref(0);
const currentPage = ref(1);
const pageSize = ref(DEFAULT_PAGE_SIZE);
const rolesMap = ref<Record<number, RoleVO[]>>({});

const searchForm = reactive<{ keyword: string; status?: number }>({ keyword: "", status: undefined });

const stats = reactive({ total: 0, normal: 0, ban: 0, logout: 0 });

/* ==================== 当前登录管理员（用于自我保护） ==================== */

const adminStore = useAdminStore();
const currentAdminId = computed(() => (adminStore.adminInfo?.id as number | undefined) ?? null);

function isSelf(row: any): boolean {
  return currentAdminId.value != null && row.id === currentAdminId.value;
}

/* ==================== 数据加载 ==================== */

function rowRoles(row: any): RoleVO[] {
  return rolesMap.value[row.id] ?? [];
}

async function loadRoles() {
  const rows = tableData.value;
  const map: Record<number, RoleVO[]> = {};
  await Promise.all(
    rows.map(async (row) => {
      try {
        const res = await getAdminRoles(row.id);
        map[row.id] = res.data.data ?? [];
      } catch {
        map[row.id] = [];
      }
    }),
  );
  rolesMap.value = map;
}

async function loadData() {
  loading.value = true;
  try {
    const res = await getAdminPage({
      pageNum: currentPage.value,
      pageSize: pageSize.value,
      keyword: searchForm.keyword || undefined,
      status: (searchForm.status ?? "") === "" ? undefined : searchForm.status,
    });
    tableData.value = res.data.data?.records ?? [];
    total.value = res.data.data?.total ?? 0;
  } catch {
    ElMessage.error("加载管理员列表失败，请稍后重试");
  } finally {
    loading.value = false;
  }
  await loadRoles();
}

async function loadStats() {
  const tasks = [
    getAdminPage({ pageNum: 1, pageSize: 1 }),
    getAdminPage({ pageNum: 1, pageSize: 1, status: 0 }),
    getAdminPage({ pageNum: 1, pageSize: 1, status: 2 }),
    getAdminPage({ pageNum: 1, pageSize: 1, status: 3 }),
  ];
  const [totalRes, normalRes, banRes, logoutRes] = await Promise.allSettled(tasks);
  const extract = (r: PromiseSettledResult<AxiosResponse<ListResult<AdminPageVO>>>) =>
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

/* ==================== 新增 / 编辑 ==================== */

interface AdminForm {
  id: number | null;
  username: string;
  password: string;
  mobile: string;
  email: string;
  sysPassword: boolean;
}

const emptyForm: AdminForm = { id: null, username: "", password: "", mobile: "", email: "", sysPassword: true };

const dialogVisible = ref(false);
const dialogTitle = ref("新增管理员");
const isEdit = ref(false);
const submitting = ref(false);
const formRef = ref<FormInstance>();
const form = reactive<AdminForm>({ ...emptyForm });

const rules = computed<FormRules>(() => {
  const base: Record<string, any[]> = {
    username: [
      { required: true, message: "请输入用户名", trigger: "blur" },
      { min: 2, max: 12, message: "用户名长度为 2-12 个字符", trigger: "blur" },
    ],
    mobile: [{ pattern: /^1[3-9]\d{9}$/, message: "手机号格式不正确", trigger: "blur" }],
    email: [{ type: "email", message: "邮箱格式不正确", trigger: "blur" }],
  };
  if (!isEdit.value && !form.sysPassword) {
    base.password = [
      { required: true, message: "请输入登录密码", trigger: "blur" },
      {
        pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)[a-zA-Z\d]{6,20}$/,
        message: "密码须包含大小写字母和数字，长度 6-20 位",
        trigger: "blur",
      },
    ];
  }
  return base as unknown as FormRules;
});

function openAdd() {
  isEdit.value = false;
  dialogTitle.value = "新增管理员";
  Object.assign(form, emptyForm);
  dialogVisible.value = true;
}

function openEdit(row: any) {
  isEdit.value = true;
  dialogTitle.value = "编辑管理员";
  Object.assign(form, {
    id: row.id,
    username: row.username,
    password: "",
    mobile: row.mobile ?? "",
    email: row.email ?? "",
    sysPassword: false,
  });
  dialogVisible.value = true;
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  submitting.value = true;
  try {
    if (isEdit.value && form.id != null) {
      const payload: AdminUpdateDTO = {
        id: form.id,
        username: form.username.trim(),
        mobile: form.mobile.trim() || undefined,
        email: form.email.trim() || undefined,
      };
      await updateAdmin(payload);
      ElMessage.success("管理员信息已更新");
    } else {
      const payload: AdminAddDTO = {
        username: form.username.trim(),
        mobile: form.mobile.trim() || undefined,
        email: form.email.trim() || undefined,
        // 使用默认密码：sysPassword 置空走后端默认密码分支；自定义密码：sysPassword=false 使用输入密码
        password: form.sysPassword ? generateValidPassword() : form.password,
        sysPassword: form.sysPassword ? null : false,
      };
      await addAdmin(payload);
      ElMessage.success(form.sysPassword ? "新增成功，账号已使用系统默认密码" : "管理员新增成功");
    }
    dialogVisible.value = false;
    await refreshAll();
  } catch {
    ElMessage.error("保存失败，请稍后重试");
  } finally {
    submitting.value = false;
  }
}

function handleDialogClosed() {
  formRef.value?.clearValidate();
}

/* ==================== 状态切换 / 封禁 ==================== */

const banVisible = ref(false);
const banTarget = ref<AdminPageVO | null>(null);
const banSubmitting = ref(false);
const banForm = reactive({ banEndTime: "", banReason: "" });

async function enableAdmin(row: AdminPageVO) {
  try {
    await ElMessageBox.confirm(`确定启用账号「${row.username}」吗？`, "启用确认", { type: "warning" });
  } catch {
    return;
  }
  try {
    await updateAdminStatus(row.id, { status: 0 });
    ElMessage.success("账号已启用");
    await refreshAll();
  } catch {
    ElMessage.error("操作失败，请稍后重试");
  }
}

function handleStatusToggle(row: any, val: string | number | boolean) {
  if (Boolean(val)) {
    enableAdmin(row);
  } else {
    banTarget.value = row;
    banForm.banEndTime = "";
    banForm.banReason = "";
    banVisible.value = true;
  }
}

async function handleBanSubmit() {
  if (!banTarget.value) return;
  banSubmitting.value = true;
  try {
    const payload: AdminStatusUpdateDTO = {
      status: 2,
      banReason: banForm.banReason.trim() || undefined,
      banEndTime: banForm.banEndTime || undefined,
    };
    await updateAdminStatus(banTarget.value.id, payload);
    ElMessage.success("账号已封禁");
    banVisible.value = false;
    await refreshAll();
  } catch {
    ElMessage.error("封禁失败，请稍后重试");
  } finally {
    banSubmitting.value = false;
  }
}

/* ==================== 重置密码 / 注销 ==================== */

async function handleResetPwd(row: any) {
  try {
    await ElMessageBox.confirm(
      `确定将「${row.username}」的密码重置为系统默认密码吗？重置后该账号需重新登录。`,
      "重置密码",
      { type: "warning", confirmButtonText: "重置", cancelButtonText: "取消" },
    );
  } catch {
    return;
  }
  try {
    await resetAdminPwd(row.id);
    ElMessage.success("密码已重置为系统默认密码");
  } catch {
    ElMessage.error("重置失败，请稍后重试");
  }
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(
      `确定注销账号「${row.username}」吗？注销后该账号将无法登录。`,
      "注销确认",
      { type: "warning", confirmButtonText: "注销", cancelButtonText: "取消", confirmButtonClass: "el-button--danger" },
    );
  } catch {
    return;
  }
  try {
    await deleteAdmin(row.id);
    ElMessage.success("账号已注销");
    await refreshAll();
  } catch {
    ElMessage.error("注销失败，请稍后重试");
  }
}

/* ==================== 分配角色 ==================== */

const assignVisible = ref(false);
const assignTarget = ref<AdminPageVO | null>(null);
const roleOptions = ref<SimpleRole[]>([]);
const selectedRoleIds = ref<number[]>([]);
const roleLoading = ref(false);
const assignSubmitting = ref(false);

async function openAssignRoles(row: any) {
  assignTarget.value = row;
  assignVisible.value = true;
  selectedRoleIds.value = [];
  roleLoading.value = true;
  try {
    const [optsRes, curRes] = await Promise.all([getAllSimpleRoles(), getAdminRoles(row.id)]);
    roleOptions.value = optsRes.data.data ?? [];
    selectedRoleIds.value = (curRes.data.data ?? []).map((r) => r.id);
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
    await assignAdminRoles(assignTarget.value.id, selectedRoleIds.value);
    ElMessage.success("角色分配成功");
    assignVisible.value = false;
    await loadRoles();
  } catch {
    ElMessage.error("角色分配失败，请稍后重试");
  } finally {
    assignSubmitting.value = false;
  }
}

/* ==================== 初始化 ==================== */

onMounted(() => {
  refreshAll();
});
</script>

<template>
  <div class="admin-manage-page">
    <!-- ==================== 统计卡 ==================== -->
    <section class="stat-grid">
      <div class="stat-card">
        <div class="stat-icon stat-icon-indigo">
          <el-icon :size="22"><UserFilled /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-num">{{ stats.total }}</div>
          <div class="stat-label">管理员总数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-green">
          <el-icon :size="22"><CircleCheckFilled /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-num">{{ stats.normal }}</div>
          <div class="stat-label">正常启用</div>
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
          placeholder="搜索用户名 / 手机号 / 邮箱"
          clearable
          style="width: 250px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-select v-model="searchForm.status" placeholder="全部状态" clearable style="width: 130px" @change="handleSearch">
          <el-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
        <el-button type="primary" @click="handleSearch">
          <el-icon class="btn-icon"><Search /></el-icon>查询
        </el-button>
        <el-button @click="handleReset">
          <el-icon class="btn-icon"><RefreshRight /></el-icon>重置
        </el-button>
        <div class="toolbar-spacer" />
        <el-button type="primary" @click="openAdd">
          <el-icon class="btn-icon"><Plus /></el-icon>新增管理员
        </el-button>
      </div>

      <!-- 表格 -->
      <el-table v-loading="loading" :data="tableData" class="admin-table">
        <el-table-column prop="id" label="ID" width="76" />
        <el-table-column label="用户名" min-width="150">
          <template #default="{ row }">
            <div class="user-cell">
              <span class="user-name">{{ row.username }}</span>
              <span v-if="isSelf(row)" class="me-tag">我</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="mobile" label="手机号" width="140">
          <template #default="{ row }">
            <span class="muted-text">{{ row.mobile || "—" }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="muted-text">{{ row.email || "—" }}</span>
          </template>
        </el-table-column>
        <el-table-column label="角色" min-width="150">
          <template #default="{ row }">
            <div class="role-tags">
              <template v-for="role in rowRoles(row).slice(0, 2)" :key="role.id">
                <span class="role-tag" :style="roleStyle(role)">{{ role.name || role.roleKey }}</span>
              </template>
              <span v-if="rowRoles(row).length > 2" class="role-more">+{{ rowRoles(row).length - 2 }}</span>
              <span v-if="!rowRoles(row).length" class="role-empty">—</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="150">
          <template #default="{ row }">
            <div class="status-cell">
              <span class="status-badge" :style="statusBadgeStyle(row.status)">
                <span class="status-dot" :style="{ background: STATUS_META[row.status]?.color ?? '#94a3b8' }" />
                {{ statusLabel(row.status) }}
              </span>
              <el-switch
                :model-value="row.status === 0"
                :disabled="isSelf(row) || row.status === 3"
                size="small"
                @change="(val: string | number | boolean) => handleStatusToggle(row, val)"
              />
            </div>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">
            <span class="time-text">{{ formatTime(row.createdTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <div class="op-cell">
              <el-button type="primary" text size="small" @click="openEdit(row)">编辑</el-button>
              <el-button type="warning" text size="small" @click="handleResetPwd(row)">重置密码</el-button>
              <el-button type="primary" text size="small" :disabled="isSelf(row)" @click="openAssignRoles(row)">分配角色</el-button>
              <el-button type="danger" text size="small" :disabled="isSelf(row)" @click="handleDelete(row)">注销</el-button>
            </div>
          </template>
        </el-table-column>
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

    <!-- ==================== 新增 / 编辑弹窗 ==================== -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="520px"
      align-center
      :close-on-click-modal="false"
      @closed="handleDialogClosed"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="2-12 个字符，不可为保留字" maxlength="12" show-word-limit />
        </el-form-item>
        <template v-if="!isEdit">
          <el-form-item label="初始密码">
            <div class="pwd-row">
              <el-switch v-model="form.sysPassword" active-text="使用默认密码" inactive-text="自定义密码" />
              <span class="form-tip">{{ form.sysPassword ? "系统默认密码：Admin@123456" : "请输入登录密码（须含大小写字母和数字）" }}</span>
            </div>
          </el-form-item>
          <el-form-item v-if="!form.sysPassword" label="自定义密码" prop="password">
            <el-input v-model="form.password" type="password" show-password placeholder="6-20 位，须包含大小写字母和数字" />
          </el-form-item>
        </template>
        <el-form-item label="手机号" prop="mobile">
          <el-input v-model="form.mobile" placeholder="选填，11 位手机号" maxlength="11" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="选填，用于接收系统通知" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取 消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确 定</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 封禁弹窗 ==================== -->
    <el-dialog v-model="banVisible" :title="`封禁账号：${banTarget?.username ?? ''}`" width="480px" align-center :close-on-click-modal="false">
      <el-form label-position="top">
        <el-form-item label="封禁截止时间">
          <el-date-picker
            v-model="banForm.banEndTime"
            type="datetime"
            placeholder="留空表示永久封禁"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
            :disabled-date="(d: Date) => d.getTime() < Date.now()"
          />
        </el-form-item>
        <el-form-item label="封禁理由">
          <el-input v-model="banForm.banReason" type="textarea" :rows="3" placeholder="请输入封禁理由" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="banVisible = false">取 消</el-button>
        <el-button type="danger" :loading="banSubmitting" @click="handleBanSubmit">确认封禁</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 分配角色弹窗 ==================== -->
    <el-dialog v-model="assignVisible" :title="`分配角色：${assignTarget?.username ?? ''}`" width="480px" align-center :close-on-click-modal="false">
      <el-form label-position="top">
        <el-form-item label="角色">
          <el-select
            v-model="selectedRoleIds"
            multiple
            filterable
            placeholder="请选择角色"
            style="width: 100%"
            :loading="roleLoading"
          >
            <el-option v-for="r in roleOptions" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
          <div class="form-tip">保存后将覆盖该管理员原有角色</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">取 消</el-button>
        <el-button type="primary" :loading="assignSubmitting" @click="handleAssignSubmit">确 定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.admin-manage-page {
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

/* ==================== 统计卡 ==================== */

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

.admin-table {
  --el-table-header-bg-color: #f8fafc;
  --el-table-header-text-color: #475569;
  --el-table-border-color: #eef1f6;
  --el-table-row-hover-bg-color: #f8faff;
  width: 100%;
  border-radius: 10px;
  overflow: hidden;
}

.admin-table :deep(.el-table__header th) {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.3px;
}

.admin-table :deep(.el-table__cell) {
  padding: 12px 0;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.user-name {
  font-weight: 600;
  color: #0f172a;
}

.me-tag {
  font-size: 10px;
  font-weight: 600;
  color: #6366f1;
  background: #eef2ff;
  border: 1px solid #e0e7ff;
  border-radius: 6px;
  padding: 1px 6px;
  line-height: 1.4;
}

.muted-text {
  font-size: 12px;
  color: #475569;
}

.role-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.role-tag {
  display: inline-block;
  padding: 2px 9px;
  border-radius: 999px;
  border: 1px solid;
  font-size: 11.5px;
  line-height: 1.5;
  white-space: nowrap;
}

.role-more {
  font-size: 11.5px;
  color: #94a3b8;
  line-height: 22px;
}

.role-empty {
  font-size: 12px;
  color: #cbd5e1;
}

.status-cell {
  display: flex;
  align-items: center;
  gap: 10px;
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
  gap: 2px;
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

/* ==================== 弹窗表单 ==================== */

.pwd-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  width: 100%;
}

.form-tip {
  margin-top: 6px;
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.6;
}

.pwd-row .form-tip {
  margin-top: 0;
}

.admin-form :deep(.el-form-item__label) {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}
</style>
