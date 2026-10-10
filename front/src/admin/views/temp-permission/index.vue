<template>
  <div class="temp-permission-page">
    <!-- ==================== 顶部 ==================== -->
    <div class="page-header">
      <div class="page-title-wrap">
        <div class="page-title">临时权限管理</div>
        <div class="page-desc">
          为前台用户或后台管理员授予临时的系统访问权限，可设置生效/过期时间，过期后自动失效，也可随时手动作废。
        </div>
      </div>
    </div>

    <!-- ==================== 主卡片 ==================== -->
    <div class="content-card">
      <!-- Tab 切换 -->
      <el-tabs v-model="activeTab" class="type-tabs" @tab-change="handleTabChange">
        <el-tab-pane label="前台用户" name="user" />
        <el-tab-pane label="后台管理员" name="admin" />
      </el-tabs>

      <!-- 人员列表 -->
      <el-table v-loading="loading" :data="tableData" stripe style="width: 100%">
        <el-table-column prop="personId" label="人员ID" width="120" align="center" />
        <el-table-column prop="personUsername" label="用户名" min-width="180" show-overflow-tooltip />
        <el-table-column prop="permCount" label="临时权限数" width="120" align="center">
          <template #default="{ row }">
            <span v-if="row.permCount != null" class="perm-count">{{ row.permCount }}</span>
            <span v-else class="muted-text">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" text size="small" @click="handleView(row as PageRow)">查看权限</el-button>
            <el-button type="primary" text size="small" @click="handleGrant(row as PageRow)">授予权限</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无人员数据" :image-size="80" />
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
    </div>

    <!-- ==================== 详情抽屉 ==================== -->
    <el-drawer
      v-model="detailVisible"
      :title="detailTitle"
      size="560px"
      :destroy-on-close="true"
      @closed="detailList = []"
    >
      <div v-loading="detailLoading">
        <template v-if="detailList.length">
          <el-timeline class="perm-timeline">
            <el-timeline-item
              v-for="item in detailList"
              :key="item.id"
              :type="statusType(item.status)"
              :hollow="item.status !== 0"
              placement="top"
            >
              <div class="perm-timeline-card">
                <div class="perm-timeline-head">
                  <span class="perm-name">{{ item.permissionName }}</span>
                  <el-tag :type="statusTagType(item.status)" size="small" effect="light" round>
                    {{ statusText(item.status) }}
                  </el-tag>
                </div>
                <div class="perm-perms">
                  <el-tag v-for="p in item.perms" :key="p" size="small" class="perm-code-tag" effect="plain" type="info">
                    {{ p }}
                  </el-tag>
                  <span v-if="!item.perms?.length" class="muted-text">无权限码</span>
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
                <div v-if="item.status === 0" class="perm-revoke">
                  <el-button size="small" class="op-btn op-revoke" text @click="handleRevoke(item)">
                    <el-icon><CircleClose /></el-icon>手动作废
                  </el-button>
                </div>
              </div>
            </el-timeline-item>
          </el-timeline>
        </template>
        <el-empty v-else description="该人员暂无临时权限" :image-size="80" />
      </div>
    </el-drawer>

    <!-- ==================== 授予权限弹窗 ==================== -->
    <el-dialog
      v-model="grantVisible"
      :title="`授予临时权限 · ${grantPerson.personId ?? ''}`"
      width="640px"
      align-center
      :close-on-click-modal="false"
      @closed="handleGrantDialogClosed"
    >
      <el-form ref="grantFormRef" :model="grantForm" :rules="grantRules" label-position="top" class="grant-form">
        <el-form-item label="人员ID">
          <el-input :model-value="String(grantPerson.personId ?? '')" disabled />
        </el-form-item>
        <el-form-item label="人员名称">
          <el-input :model-value="grantPerson.personUsername ?? '—'" disabled />
        </el-form-item>

        <el-form-item label="选择权限" prop="permissionIdList">
          <div class="perm-tree-wrap" :class="{ 'tree-error': treeError }">
            <el-tree
              ref="permTreeRef"
              :data="permissionTree"
              :props="permTreeProps"
              node-key="id"
              show-checkbox
              default-expand-all
              class="perm-tree"
              @check="handleTreeCheck"
            />
          </div>
          <div v-if="treeError" class="tree-error-text">{{ treeError }}</div>
          <div class="form-tip">勾选需要授予的权限项，可展开分组查看具体权限码。</div>
        </el-form-item>

        <div class="grant-time-row">
          <el-form-item label="生效时间" prop="startTime" class="grant-time-item">
            <el-date-picker
              v-model="grantForm.startTime"
              type="datetime"
              placeholder="选择生效时间"
              value-format="YYYY-MM-DD HH:mm:ss"
              style="width: 100%"
              :clearable="false"
            />
          </el-form-item>
          <el-form-item label="过期时间" prop="expireTime" class="grant-time-item">
            <el-date-picker
              v-model="grantForm.expireTime"
              type="datetime"
              placeholder="选择过期时间"
              value-format="YYYY-MM-DD HH:mm:ss"
              style="width: 100%"
              :clearable="false"
            />
          </el-form-item>
        </div>

        <el-form-item label="授予原因" prop="grantReason">
          <el-input
            v-model="grantForm.grantReason"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="请填写授予该临时权限的原因（必填）"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="grantVisible = false">取 消</el-button>
        <el-button type="primary" :loading="grantSubmitting" @click="handleGrantSubmit">确 定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { CircleClose } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import type { FormInstance, FormRules } from "element-plus";
import { DEFAULT_PAGE_SIZE, PAGINATION_LAYOUT } from "@/shared/api/config";
import {
  pageUserTempPermissions,
  pageAdminTempPermissions,
  listUserTempPermissions,
  listAdminTempPermissions,
  grantTempPermission,
  revokeTempPermission,
} from "@/admin/api/permission/temp-permission";
import { getPermissionTree } from "@/admin/api/permission/permission";

/* ==================== 类型定义 ==================== */

type PageRow = { personId: number; personUsername: string; permCount?: number };

interface PermItem {
  id: number;
  personId: number;
  personUsername: string;
  permissionId: number;
  perms: string[];
  permissionName: string;
  groupName: string;
  startTime: string;
  expireTime: string;
  status: number;
  grantReason: string;
  grantUsername: string;
  createdTime: string;
}

interface PermTreeNode {
  id: number | string;
  name?: string;
  perms?: string[];
  permissions?: PermTreeNode[];
  children?: PermTreeNode[];
}

/* ==================== 常量 & 映射 ==================== */

const STATUS_META: Record<number, { text: string; tag: "success" | "danger" | "info"; type: "primary" | "danger" | "info" }> = {
  0: { text: "有效", tag: "success", type: "primary" },
  1: { text: "已作废", tag: "danger", type: "danger" },
  2: { text: "已过期", tag: "info", type: "info" },
};

function statusText(status?: number): string {
  return STATUS_META[status ?? 0]?.text ?? "未知";
}

function statusTagType(status?: number): "success" | "danger" | "info" {
  return STATUS_META[status ?? 0]?.tag ?? "info";
}

function statusType(status?: number): "primary" | "danger" | "info" {
  return STATUS_META[status ?? 0]?.type ?? "info";
}

function formatTime(v?: string | number): string {
  if (v === undefined || v === null || v === "") return "—";
  // 后端可能返回时间戳（number）或 ISO 字符串；number 转 Date，字符串沿用原格式
  if (typeof v === "number") {
    const d = new Date(v);
    const pad = (n: number) => String(n).padStart(2, "0");
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
  }
  return String(v).replace("T", " ").slice(0, 19);
}

/* ==================== 页面状态 ==================== */

const activeTab = ref<"user" | "admin">("user");

const loading = ref(false);
const tableData = ref<PageRow[]>([]);
const total = ref(0);
const currentPage = ref(1);
const pageSize = ref(DEFAULT_PAGE_SIZE);

/* ==================== 详情抽屉 ==================== */

const detailVisible = ref(false);
const detailLoading = ref(false);
const detailList = ref<PermItem[]>([]);
const detailTitle = ref("临时权限详情");

/* ==================== 授予权限弹窗 ==================== */

const grantVisible = ref(false);
const grantSubmitting = ref(false);
const grantFormRef = ref<FormInstance>();
const permTreeRef = ref();
const permissionTree = ref<PermTreeNode[]>([]);
const treeError = ref("");
const treeLoaded = ref(false);

const grantPerson = reactive<PageRow>({ personId: 0, personUsername: "" });

const emptyGrantForm = () => ({
  startTime: "",
  expireTime: "",
  grantReason: "",
  permissionIdList: [] as number[],
});

const grantForm = reactive(emptyGrantForm());

const grantRules: FormRules = {
  startTime: [{ required: true, message: "请选择生效时间", trigger: "change" }],
  expireTime: [{ required: true, message: "请选择过期时间", trigger: "change" }],
  grantReason: [{ required: true, message: "请填写授予原因", trigger: "blur" }],
};

const permTreeProps = { label: "name", children: "permissions" };

/* ==================== 数据加载：人员列表 ==================== */

function buildParams() {
  return {
    pageNum: currentPage.value,
    pageSize: pageSize.value,
  };
}

async function loadData() {
  loading.value = true;
  try {
    const fetcher = activeTab.value === "user" ? pageUserTempPermissions : pageAdminTempPermissions;
    const res: { data: { data: { records: PageRow[]; total: number } } } = await fetcher(buildParams());
    tableData.value = res.data.data?.records ?? [];
    total.value = res.data.data?.total ?? 0;
  } catch {
    ElMessage.error("加载临时权限人员失败，请稍后重试");
  } finally {
    loading.value = false;
  }
}

function handleTabChange() {
  currentPage.value = 1;
  loadData();
}

/* ==================== 交互：详情抽屉 ==================== */

function tabText(): string {
  return activeTab.value === "user" ? "前台用户" : "后台管理员";
}

async function handleView(row: PageRow) {
  detailTitle.value = `${tabText()} · ${row.personUsername} 的临时权限`;
  detailList.value = [];
  detailVisible.value = true;
  detailLoading.value = true;
  try {
    const fetcher = activeTab.value === "user" ? listUserTempPermissions : listAdminTempPermissions;
    const res: { data: { data: PermItem[] } } = await fetcher(row.personId);
    detailList.value = res.data.data ?? [];
  } catch {
    ElMessage.error("加载临时权限详情失败，请稍后重试");
  } finally {
    detailLoading.value = false;
  }
}

async function handleRevoke(item: PermItem) {
  try {
    await ElMessageBox.confirm(
      `确定要作废「${item.permissionName}」的临时权限吗？作废后该权限立即失效。`,
      "手动作废确认",
      { type: "warning", confirmButtonText: "作废", cancelButtonText: "取消", confirmButtonClass: "el-button--danger" },
    );
  } catch {
    return;
  }
  try {
    await revokeTempPermission(item.id);
    ElMessage.success(`「${item.permissionName}」已作废`);
    item.status = 1;
    await loadData();
  } catch {
    ElMessage.error("作废失败，请稍后重试");
  }
}

/* ==================== 交互：授予权限 ==================== */

async function loadPermissionTree() {
  if (treeLoaded.value) return;
  try {
    const res: { data: { data: PermTreeNode[] } } = await getPermissionTree();
    permissionTree.value = res.data.data ?? [];
    treeLoaded.value = true;
  } catch {
    ElMessage.error("加载权限树失败，请稍后重试");
  }
}

async function handleGrant(row: PageRow) {
  Object.assign(grantPerson, { personId: row.personId, personUsername: row.personUsername });
  Object.assign(grantForm, emptyGrantForm());
  treeError.value = "";
  if (permTreeRef.value) permTreeRef.value.setCheckedKeys([]);
  grantVisible.value = true;
  await loadPermissionTree();
}

function handleTreeCheck(_node: unknown, checkState: { checkedKeys: unknown[]; halfCheckedKeys?: unknown[] }) {
  grantForm.permissionIdList = [...(checkState.checkedKeys as number[])];
}

function handleGrantDialogClosed() {
  grantFormRef.value?.clearValidate();
  treeError.value = "";
}

async function handleGrantSubmit() {
  const valid = await grantFormRef.value?.validate().catch(() => false);
  if (!valid) return;
  if (!grantForm.permissionIdList.length) {
    treeError.value = "请至少勾选一个权限项";
    ElMessage.warning("请至少勾选一个权限项");
    return;
  }
  if (grantForm.expireTime <= grantForm.startTime) {
    ElMessage.warning("过期时间必须晚于生效时间");
    return;
  }
  grantSubmitting.value = true;
  try {
    await grantTempPermission({
      personId: grantPerson.personId,
      permissionIdList: grantForm.permissionIdList,
      startTime: grantForm.startTime,
      expireTime: grantForm.expireTime,
      grantReason: grantForm.grantReason,
    });
    ElMessage.success("临时权限授予成功");
    grantVisible.value = false;
    await loadData();
  } catch {
    ElMessage.error("授予失败，请稍后重试");
  } finally {
    grantSubmitting.value = false;
  }
}

/* ==================== 初始化 ==================== */

onMounted(() => {
  loadData();
});
</script>

<style scoped>
.temp-permission-page {
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

/* ==================== 顶部 ==================== */

.page-header {
  background: #fff;
  border-radius: 14px;
  padding: 18px 20px;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.04);
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: #1e293b;
}

.page-desc {
  margin-top: 6px;
  font-size: 13px;
  color: #64748b;
  line-height: 1.6;
}

/* ==================== 主卡片 ==================== */

.content-card {
  background: #fff;
  border-radius: 14px;
  padding: 20px;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.04);
}

.type-tabs {
  margin-bottom: 16px;
}

.perm-count {
  font-weight: 600;
  color: #6366f1;
}

.muted-text {
  color: #94a3b8;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
}

.page-total {
  font-size: 13px;
  color: #64748b;
}

/* ==================== 详情抽屉 ==================== */

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

.perm-revoke {
  display: flex;
  justify-content: flex-end;
}

.op-btn {
  font-weight: 500;
}

.op-revoke {
  color: #ef4444;
}

/* ==================== 授予权限弹窗 ==================== */

.grant-form :deep(.el-form-item__label) {
  font-weight: 500;
  color: #334155;
}

.perm-tree-wrap {
  width: 100%;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  max-height: 260px;
  overflow: auto;
  padding: 6px 10px;
  box-sizing: border-box;
}

.perm-tree-wrap.tree-error {
  border-color: #f56c6c;
}

.perm-tree {
  --el-tree-node-content-height: 30px;
}

.tree-error-text {
  color: #f56c6c;
  font-size: 12px;
  margin-top: 4px;
}

.form-tip {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 6px;
}

.grant-time-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.grant-time-item {
  margin-bottom: 18px;
}
</style>
