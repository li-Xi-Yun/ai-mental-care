<template>
  <div class="permission-page">
    <div class="pl-header">
      <div class="pl-header-left">
        <h2 class="pl-title">权限管理</h2>
        <p v-if="permissionTree.length" class="pl-subtitle">共 {{ totalCount }} 项权限，分布于 {{ groupCount }} 个分组，{{ disabledCount }} 项停用</p>
      </div>
      <el-button type="primary" @click="openDialog()">＋ 新增权限</el-button>
    </div>

    <div v-loading="loading" class="pl-group-list">
      <el-empty v-if="!loading && !permissionTree.length" description="暂无权限数据" />
      <div v-for="group in permissionTree" :key="group.groupName" class="pl-group-card">
        <div class="pl-group-header">
          <span class="pl-group-name">{{ group.groupName }}</span>
          <span class="pl-group-count">{{ group.permissions?.length ?? 0 }} 项</span>
        </div>
        <div class="pl-group-body">
          <div v-for="perm in group.permissions || []" :key="perm.id" class="pl-perm-row">
            <div class="pl-perm-info">
              <span class="pl-perm-name">{{ perm.name }}</span>
              <span class="pl-perm-code">{{ perm.perms }}</span>
            </div>
            <el-tag :type="perm.status === 1 ? 'danger' : 'success'" size="small" effect="light">
              {{ perm.status === 1 ? '停用' : '正常' }}
            </el-tag>
            <div class="pl-perm-actions">
              <el-button type="primary" text size="small" @click="openDialog(perm, group.groupName)">编辑</el-button>
              <el-button type="danger" text size="small" @click="handleDelete(perm)">删除</el-button>
            </div>
          </div>
          <div v-if="!group.permissions?.length" class="pl-empty">该分组暂无权限项</div>
        </div>
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑权限' : '新增权限'" width="420" destroy-on-close @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="权限标识" prop="perms">
          <el-input v-model="form.perms" placeholder="如 sys:permission:add" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="权限名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入权限名称" maxlength="20" />
        </el-form-item>
        <el-form-item label="分组名称" prop="groupName">
          <el-select v-model="form.groupName" placeholder="选择或输入分组" filterable allow-create style="width: 100%">
            <el-option v-for="g in permissionTree" :key="g.groupName" :label="g.groupName" :value="g.groupName" />
          </el-select>
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import type { FormInstance, FormRules } from "element-plus";
import { getPermissionTree, getPermissionDetail, addPermission, updatePermission, deletePermission } from "@/admin/api/permission/permission";
import type { Result } from "@/shared/api/types";

interface PermissionItem {
  id: number;
  perms: string;
  name: string;
  status: number;
}
interface PermissionGroup {
  groupName: string;
  permissions: PermissionItem[];
}

const loading = ref(false);
const submitting = ref(false);
const permissionTree = ref<PermissionGroup[]>([]);
const dialogVisible = ref(false);
const formRef = ref<FormInstance>();
const form = reactive({ id: 0, perms: "", name: "", groupName: "", remark: "" });

const totalCount = computed(() => permissionTree.value.reduce((s, g) => s + (g.permissions?.length ?? 0), 0));
const groupCount = computed(() => permissionTree.value.length);
const disabledCount = computed(() => permissionTree.value.reduce((s, g) => s + (g.permissions?.filter((p) => p.status === 1).length ?? 0), 0));

const rules: FormRules = {
  perms: [
    { required: true, message: "请输入权限标识", trigger: "blur" },
    { min: 2, max: 50, message: "长度 2-50", trigger: "blur" },
  ],
  name: [
    { required: true, message: "请输入权限名称", trigger: "blur" },
    { min: 2, max: 20, message: "长度 2-20", trigger: "blur" },
  ],
  groupName: [{ required: true, message: "请输入分组名称", trigger: "change" }],
};

async function fetchTree() {
  loading.value = true;
  try {
    const res = (await getPermissionTree()).data as Result<PermissionGroup[]>;
    permissionTree.value = res.data || [];
  } catch {
    ElMessage.error("加载权限列表失败");
  } finally {
    loading.value = false;
  }
}

function resetForm() {
  form.id = 0;
  form.perms = "";
  form.name = "";
  form.groupName = "";
  form.remark = "";
  formRef.value?.clearValidate();
}

function openDialog(perm?: PermissionItem, groupName?: string) {
  resetForm();
  if (perm) {
    form.id = perm.id;
    form.perms = perm.perms;
    form.name = perm.name;
    form.groupName = groupName ?? "";
    getPermissionDetail(perm.id)
      .then((res) => {
        form.remark = (res.data as Result<any>).data?.remark ?? "";
      })
      .catch(() => undefined);
  }
  dialogVisible.value = true;
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  submitting.value = true;
  try {
    const payload = { perms: form.perms, name: form.name, groupName: form.groupName, remark: form.remark };
    if (form.id) {
      await updatePermission(form.id, payload);
      ElMessage.success("权限已更新");
    } else {
      await addPermission(payload);
      ElMessage.success("权限已新增");
    }
    dialogVisible.value = false;
    await fetchTree();
  } catch {
    ElMessage.error("操作失败");
  } finally {
    submitting.value = false;
  }
}

async function handleDelete(perm: PermissionItem) {
  try {
    await ElMessageBox.confirm(`确认删除权限「${perm.name}」(${perm.perms})？`, "删除确认", { type: "warning" });
  } catch {
    return;
  }
  try {
    await deletePermission(perm.id);
    ElMessage.success("删除成功");
    await fetchTree();
  } catch {
    ElMessage.error("删除失败");
  }
}

onMounted(fetchTree);
</script>

<style scoped>
.permission-page {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  min-height: 400px;
}
.pl-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 18px;
}
.pl-title {
  font-size: 18px;
  font-weight: 600;
  color: #1e293b;
}
.pl-subtitle {
  font-size: 12px;
  color: #64748b;
  margin-top: 4px;
}
.pl-group-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.pl-group-card {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  overflow: hidden;
}
.pl-group-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
}
.pl-group-name {
  font-size: 14px;
  font-weight: 600;
  color: #334155;
}
.pl-group-count {
  font-size: 12px;
  color: #94a3b8;
}
.pl-group-body {
  padding: 4px 16px;
}
.pl-perm-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid #f1f5f9;
}
.pl-perm-row:last-child {
  border-bottom: none;
}
.pl-perm-info {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.pl-perm-name {
  font-size: 13px;
  font-weight: 500;
  color: #334155;
  flex-shrink: 0;
}
.pl-perm-code {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 12px;
  color: #6366f1;
  background: #eef2ff;
  border-radius: 4px;
  padding: 1px 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.pl-perm-actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}
.pl-empty {
  padding: 16px 0;
  text-align: center;
  color: #94a3b8;
  font-size: 12px;
}
</style>