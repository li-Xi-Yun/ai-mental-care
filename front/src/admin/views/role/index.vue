<template>
  <div class="role-page">
    <div class="rp-header">
      <div class="rp-header-left">
        <h2 class="rp-title">角色管理</h2>
        <p class="rp-subtitle">管理系统的角色及其权限分配，角色权限会实时作用于账号的接口访问</p>
      </div>
      <el-button type="primary" @click="openDialog()">＋ 新增角色</el-button>
    </div>

    <div class="rp-search">
      <el-input v-model="query.name" placeholder="角色名称" clearable style="width: 180px" @keyup.enter="handleSearch" />
      <el-input v-model="query.roleKey" placeholder="角色标识" clearable style="width: 180px" @keyup.enter="handleSearch" />
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px">
        <el-option label="正常" :value="0" />
        <el-option label="停用" :value="1" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
      <el-button @click="handleReset">重置</el-button>
    </div>

    <div class="rp-table-wrap">
      <el-table v-loading="loading" :data="roleList" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="角色名称" min-width="160">
          <template #default="{ row }">
            <span class="rp-role-name">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column label="角色标识" min-width="160">
          <template #default="{ row }">
            <span class="rp-role-key">{{ row.roleKey }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status"
              :active-value="0"
              :inactive-value="1"
              active-text=""
              inline-prompt
              @change="(v: string | number | boolean) => handleToggleStatus(row, v)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || "—" }}</template>
        </el-table-column>
        <el-table-column prop="createdTime" label="创建时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createdTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" text size="small" @click="openPermissionDialog(row)">权限</el-button>
            <el-button type="warning" text size="small" @click="openDialog(row)">编辑</el-button>
            <el-button type="danger" text size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="rp-pagination">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :layout="PAGINATION_LAYOUT"
          background
          @current-change="fetchRoles"
          @size-change="handleSizeChange"
        />
      </div>
    </div>

    <!-- 新增/编辑角色弹窗 -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑角色' : '新增角色'" width="440" destroy-on-close @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="角色名称" prop="name">
          <el-input v-model="form.name" placeholder="如 心理咨询师" maxlength="20" />
        </el-form-item>
        <el-form-item label="角色标识" prop="roleKey">
          <el-input v-model="form.roleKey" placeholder="如 counselor" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="选填" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 权限分配弹窗 -->
    <el-dialog v-model="permDialogVisible" :title="`分配权限 - ${currentRole?.name ?? ''}`" width="520" destroy-on-close>
      <div v-loading="permLoading" class="rp-tree-wrap">
        <el-tree
          ref="treeRef"
          :data="permTree"
          node-key="id"
          show-checkbox
          default-expand-all
          :props="{ label: 'name', children: 'permissions' }"
          :check-strictly="false"
        />
      </div>
      <template #footer>
        <el-button @click="permDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="permSubmitting" @click="handleSavePermissions">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Search } from "@element-plus/icons-vue";
import type { FormInstance, FormRules, ElTree } from "element-plus";
import {
  getRolePage,
  addRole,
  updateRole,
  updateRoleStatus,
  deleteRole,
  getRolePermissions,
  assignRolePermissions,
} from "@/admin/api/permission/role";
import { getPermissionTree } from "@/admin/api/permission/permission";
import { DEFAULT_PAGE_SIZE, PAGINATION_LAYOUT } from "@/shared/api/config";
import dayjs from "@/shared/utils/dayjs";
import type { Result } from "@/shared/api/types";

interface RoleItem {
  id: number;
  name: string;
  roleKey: string;
  status: number;
  remark?: string;
  createdTime?: string;
}

const loading = ref(false);
const submitting = ref(false);
const roleList = ref<RoleItem[]>([]);
const total = ref(0);
const dialogVisible = ref(false);
const formRef = ref<FormInstance>();
const form = reactive({ id: 0, name: "", roleKey: "", remark: "" });

const query = reactive({ pageNum: 1, pageSize: DEFAULT_PAGE_SIZE, name: "", roleKey: "", status: undefined as number | undefined });

const permDialogVisible = ref(false);
const permLoading = ref(false);
const permSubmitting = ref(false);
const currentRole = ref<RoleItem>();
const permTree = ref<any[]>([]);
const treeRef = ref<InstanceType<typeof ElTree>>();

const rules: FormRules = {
  name: [
    { required: true, message: "请输入角色名称", trigger: "blur" },
    { min: 2, max: 20, message: "长度 2-20", trigger: "blur" },
  ],
  roleKey: [{ required: true, message: "请输入角色标识", trigger: "blur" }],
};

function formatTime(t?: string) {
  return t ? dayjs(t).format("YYYY-MM-DD HH:mm") : "—";
}

async function fetchRoles() {
  loading.value = true;
  try {
    const res = (await getRolePage({ ...query })).data as Result<any>;
    const data = res.data ?? {};
    roleList.value = data.records ?? [];
    total.value = data.total ?? 0;
  } catch {
    ElMessage.error("加载角色列表失败");
  } finally {
    loading.value = false;
  }
}

function handleSearch() {
  query.pageNum = 1;
  fetchRoles();
}

function handleReset() {
  query.name = "";
  query.roleKey = "";
  query.status = undefined;
  query.pageNum = 1;
  fetchRoles();
}

function handleSizeChange() {
  query.pageNum = 1;
  fetchRoles();
}

function resetForm() {
  form.id = 0;
  form.name = "";
  form.roleKey = "";
  form.remark = "";
  formRef.value?.clearValidate();
}

function openDialog(row?: any) {
  resetForm();
  if (row) {
    form.id = row.id;
    form.name = row.name;
    form.roleKey = row.roleKey;
    form.remark = row.remark ?? "";
  }
  dialogVisible.value = true;
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  submitting.value = true;
  try {
    const payload = { name: form.name, roleKey: form.roleKey, remark: form.remark };
    if (form.id) {
      await updateRole(form.id, payload);
      ElMessage.success("已更新");
    } else {
      await addRole(payload);
      ElMessage.success("已新增");
    }
    dialogVisible.value = false;
    await fetchRoles();
  } catch {
    ElMessage.error("保存失败");
  } finally {
    submitting.value = false;
  }
}

async function handleToggleStatus(row: any, val: string | number | boolean) {
  const next = val === 0 || val === false ? 0 : 1;
  try {
    await updateRoleStatus(row.id, next);
    row.status = next;
    ElMessage.success(next === 0 ? "已启用" : "已停用");
  } catch {
    ElMessage.error("操作失败");
  }
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确认删除角色「${row.name}」？`, "删除确认", { type: "warning" });
  } catch {
    return;
  }
  try {
    await deleteRole(row.id);
    ElMessage.success("已删除");
    await fetchRoles();
  } catch {
    ElMessage.error("删除失败");
  }
}

async function openPermissionDialog(row: any) {
  currentRole.value = row;
  permDialogVisible.value = true;
  permLoading.value = true;
  try {
    const treeRes = (await getPermissionTree()).data as Result<any[]>;
    // 分组树：父节点用 groupName，子节点是 permissions
    const nodes = (treeRes.data ?? []).map((g) => ({
      id: `g-${g.groupName}`,
      name: g.groupName,
      disabled: true,
      permissions: (g.permissions ?? []).map((p: any) => ({
        id: p.id,
        name: p.name,
      })),
    }));
    permTree.value = nodes;
    const allIds = nodes.flatMap((n: any) => n.permissions.map((p: any) => p.id));
    const permRes = (await getRolePermissions(row.id)).data as Result<any[]>;
    const bound: number[] = [];
    for (const g of permRes.data ?? []) {
      for (const p of g.permissions ?? []) bound.push(p.id);
    }
    // 等树渲染后设置勾选
    setTimeout(() => {
      treeRef.value?.setCheckedKeys(bound.filter((id) => allIds.includes(id)));
    }, 50);
  } catch {
    ElMessage.error("加载权限失败");
  } finally {
    permLoading.value = false;
  }
}

async function handleSavePermissions() {
  const checked = treeRef.value?.getCheckedKeys() ?? [];
  const halfChecked = treeRef.value?.getHalfCheckedKeys() ?? [];
  const ids = [...checked, ...halfChecked]
    .map(Number)
    .filter((id) => Number.isFinite(id) && id > 0);
  if (!currentRole.value) return;
  permSubmitting.value = true;
  try {
    await assignRolePermissions(currentRole.value.id, [...new Set(ids)]);
    ElMessage.success("权限已更新");
    permDialogVisible.value = false;
  } catch {
    ElMessage.error("分配失败");
  } finally {
    permSubmitting.value = false;
  }
}

onMounted(fetchRoles);
</script>

<style scoped>
.role-page {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.rp-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}
.rp-title {
  font-size: 18px;
  font-weight: 600;
  color: #1e293b;
}
.rp-subtitle {
  font-size: 12px;
  color: #64748b;
  margin-top: 4px;
}
.rp-search {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.rp-table-wrap {
  border: 1px solid #f1f5f9;
  border-radius: 8px;
  overflow: hidden;
}
.rp-role-name {
  font-weight: 500;
  color: #334155;
}
.rp-role-key {
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 12px;
  color: #6366f1;
  background: #eef2ff;
  padding: 1px 6px;
  border-radius: 4px;
}
.rp-pagination {
  display: flex;
  justify-content: flex-end;
  padding: 12px 16px;
  background: #fff;
}
.rp-tree-wrap {
  max-height: 400px;
  overflow-y: auto;
  border: 1px solid #f1f5f9;
  border-radius: 8px;
  padding: 8px;
}
</style>