<!-- 授予临时权限弹窗：选择被授权人 + 平铺复选权限 + 生效/过期时间 + 授予原因，校验后回传载荷 -->
<template>
  <el-dialog
    :model-value="modelValue"
    title="授予临时权限"
    width="680px"
    align-center
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="handleClosed"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="grant-form">
      <!-- ==================== 1. 选择被授权人 ==================== -->
      <div class="grant-field">
        <div class="grant-field-head">
          <span class="grant-field-label">选择被授权人（必选）</span>
        </div>

        <div class="person-toolbar">
          <div class="seg-control">
            <span
              class="seg-control-item"
              :class="{ active: personType === 'user' }"
              @click="handleTypeChange('user')"
              >用户</span
            >
            <span
              class="seg-control-item"
              :class="{ active: personType === 'admin' }"
              @click="handleTypeChange('admin')"
              >管理员</span
            >
          </div>
          <el-input
            v-model="personKeyword"
            class="person-search"
            placeholder="搜索用户名或登录账号"
            clearable
            @keyup.enter="loadPersons()"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button @click="loadPersons()">搜索</el-button>
        </div>

        <div v-loading="personLoading" class="person-list" :class="{ 'field-error': !!errors.person }">
          <div v-if="!personOptions.length" class="picker-empty">未找到账号</div>
          <template v-else>
            <label
              v-for="opt in personOptions"
              :key="`${opt.type}-${opt.id}`"
              class="person-option"
              :class="{ active: isSelected(opt) }"
            >
              <input
                type="radio"
                name="grant-person"
                class="person-radio"
                :checked="isSelected(opt)"
                @change="selectPerson(opt)"
              />
              <span class="person-avatar" :style="{ background: hashColorFor(opt.username, 4).gradient }">
                {{ initialOf(opt.username) }}
              </span>
              <span class="person-info">
                <span class="person-name">{{ opt.username || "—" }}</span>
                <span class="person-account">@{{ opt.loginAccount || "—" }}</span>
              </span>
              <span class="person-type">{{ opt.type === "user" ? "用户" : "管理员" }}</span>
            </label>
          </template>
        </div>
        <div v-if="errors.person" class="field-error-text">{{ errors.person }}</div>
      </div>

      <!-- ==================== 2. 选择权限 ==================== -->
      <div class="grant-field">
        <div class="grant-field-head">
          <span class="grant-field-label">选择权限（可多选）</span>
          <span class="grant-field-actions">
            <button type="button" class="link-btn" @click="selectAllPerms">全选</button>
            <button type="button" class="link-btn link-muted" @click="clearPerms">清空</button>
          </span>
        </div>

        <div v-loading="permLoading" class="perm-box" :class="{ 'field-error': !!errors.perms }">
          <div v-if="!permissionTree.length" class="picker-empty">暂无可用权限</div>
          <div v-else class="perm-groups">
            <div v-for="group in permissionTree" :key="group.groupName" class="perm-group">
              <div class="perm-group-name">{{ group.groupName }}</div>
              <div class="perm-pills">
                <label
                  v-for="p in group.permissions"
                  :key="p.id"
                  class="perm-pill"
                  :class="{ checked: isPermChecked(p.id) }"
                >
                  <input
                    type="checkbox"
                    class="perm-checkbox"
                    :checked="isPermChecked(p.id)"
                    @change="togglePerm(p.id, ($event.target as HTMLInputElement).checked)"
                  />
                  <span class="perm-pill-name">{{ p.name || "—" }}</span>
                  <code v-if="permText(p.perms)" class="perm-pill-code">{{ permText(p.perms) }}</code>
                </label>
              </div>
            </div>
          </div>
        </div>
        <div v-if="errors.perms" class="field-error-text">{{ errors.perms }}</div>
      </div>

      <!-- ==================== 3. 生效 / 过期时间 ==================== -->
      <div class="grant-time-row">
        <el-form-item label="生效时间（必填）" prop="startTime">
          <el-date-picker
            v-model="form.startTime"
            type="datetime"
            placeholder="选择生效时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
            :clearable="false"
          />
        </el-form-item>
        <el-form-item label="过期时间（必填）" prop="expireTime">
          <el-date-picker
            v-model="form.expireTime"
            type="datetime"
            placeholder="选择过期时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
            :clearable="false"
          />
        </el-form-item>
      </div>

      <!-- ==================== 4. 授权原因 ==================== -->
      <el-form-item label="授权原因（必填）" prop="grantReason">
        <el-input
          v-model="form.grantReason"
          type="textarea"
          :rows="3"
          maxlength="200"
          show-word-limit
          placeholder="请填写授予该临时权限的原因（必填）"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取 消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">确认授予</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import type { FormInstance, FormRules } from "element-plus";
import { Search } from "@element-plus/icons-vue";
import dayjs from "@/shared/utils/dayjs";
import { hashColorFor } from "@/admin/composables/useHashColor";
import { getUserPage } from "@/admin/api/admin/user-manage";
import { getAdminPage } from "@/admin/api/admin/admin-manage";
import type { GrantSubmitPayload, PermGroup } from "../useTempPermissionList";

const props = withDefaults(
  defineProps<{
    modelValue: boolean;
    permissionTree: PermGroup[];
    permLoading?: boolean;
    submitting?: boolean;
  }>(),
  { permLoading: false, submitting: false },
);

const emit = defineEmits<{
  (e: "update:modelValue", v: boolean): void;
  (e: "submit", payload: GrantSubmitPayload): void;
}>();

/** 候选被授权人 */
interface PersonOption {
  id: number;
  username: string;
  loginAccount: string;
  type: "user" | "admin";
}

/* ==================== 表单状态 ==================== */

const formRef = ref<FormInstance>();
const errors = reactive({ person: "", perms: "" });

const form = reactive({
  startTime: "",
  expireTime: "",
  grantReason: "",
  permissionIdList: [] as number[],
});

const rules: FormRules = {
  startTime: [{ required: true, message: "请选择生效时间", trigger: "change" }],
  expireTime: [{ required: true, message: "请选择过期时间", trigger: "change" }],
  grantReason: [
    {
      validator: (_rule, value: string, callback) => {
        if (value && String(value).trim()) callback();
        else callback(new Error("请填写授予原因"));
      },
      trigger: ["blur", "change"],
    },
  ],
};

/* ==================== 被授权人选择 ==================== */

const personType = ref<"user" | "admin">("user");
const personKeyword = ref("");
const personOptions = ref<PersonOption[]>([]);
const personLoading = ref(false);
const selectedPerson = ref<PersonOption | null>(null);

let personSeq = 0;

/** 加载候选账号（用户 / 管理员） */
async function loadPersons() {
  const seq = ++personSeq;
  personLoading.value = true;
  try {
    const keyword = personKeyword.value.trim() || undefined;
    if (personType.value === "user") {
      const res = await getUserPage({ pageNum: 1, pageSize: 50, keyword });
      if (seq !== personSeq) return;
      personOptions.value = (res.data.data?.records ?? []).map((u) => ({
        id: u.id,
        username: u.username ?? "",
        loginAccount: u.loginAccount ?? "",
        type: "user" as const,
      }));
    } else {
      const res = await getAdminPage({ pageNum: 1, pageSize: 50, keyword });
      if (seq !== personSeq) return;
      personOptions.value = (res.data.data?.records ?? []).map((a) => ({
        id: a.id,
        username: a.username ?? "",
        loginAccount: a.loginAccount ?? "",
        type: "admin" as const,
      }));
    }
  } catch {
    if (seq === personSeq) personOptions.value = [];
  } finally {
    if (seq === personSeq) personLoading.value = false;
  }
}

/** 切换候选类型：清空已选与关键词，重新加载首批 */
function handleTypeChange(type: "user" | "admin") {
  if (personType.value === type) return;
  personType.value = type;
  personKeyword.value = "";
  selectedPerson.value = null;
  loadPersons();
}

function selectPerson(opt: PersonOption) {
  selectedPerson.value = opt;
  errors.person = "";
}

function isSelected(opt: PersonOption): boolean {
  return selectedPerson.value?.type === opt.type && selectedPerson.value?.id === opt.id;
}

function initialOf(name?: string): string {
  return (name || "?").charAt(0).toUpperCase();
}

/* ==================== 权限选择 ==================== */

function permText(perms?: string | string[]): string {
  if (Array.isArray(perms)) return perms.join(" ");
  return perms || "";
}

function isPermChecked(id: number | string): boolean {
  return form.permissionIdList.includes(Number(id));
}

function togglePerm(id: number | string, checked: boolean) {
  const pid = Number(id);
  if (checked) {
    if (!form.permissionIdList.includes(pid)) form.permissionIdList.push(pid);
  } else {
    form.permissionIdList = form.permissionIdList.filter((i) => i !== pid);
  }
  errors.perms = "";
}

function selectAllPerms() {
  form.permissionIdList = props.permissionTree.flatMap((g) => g.permissions.map((p) => Number(p.id)));
  errors.perms = "";
}

function clearPerms() {
  form.permissionIdList = [];
}

/* ==================== 打开重置 / 关闭清理 ==================== */

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return;
    selectedPerson.value = null;
    personType.value = "user";
    personKeyword.value = "";
    form.permissionIdList = [];
    form.grantReason = "";
    errors.person = "";
    errors.perms = "";
    const now = dayjs();
    form.startTime = now.format("YYYY-MM-DD HH:mm:ss");
    form.expireTime = now.add(7, "day").format("YYYY-MM-DD HH:mm:ss");
    formRef.value?.clearValidate();
    loadPersons();
  },
);

function handleClosed() {
  formRef.value?.clearValidate();
  errors.person = "";
  errors.perms = "";
}

/* ==================== 提交 ==================== */

async function handleSubmit() {
  errors.person = selectedPerson.value ? "" : "请选择被授权人";
  errors.perms = form.permissionIdList.length ? "" : "请至少勾选一个权限项";

  const valid = await formRef.value?.validate().catch(() => false);

  if (errors.person) {
    ElMessage.warning(errors.person);
    return;
  }
  if (errors.perms) {
    ElMessage.warning(errors.perms);
    return;
  }
  if (!valid) {
    if (!form.startTime) ElMessage.warning("请选择生效时间");
    else if (!form.expireTime) ElMessage.warning("请选择过期时间");
    else ElMessage.warning("请填写授予原因");
    return;
  }
  if (form.expireTime <= form.startTime) {
    ElMessage.warning("过期时间必须晚于生效时间");
    return;
  }

  const person = selectedPerson.value as PersonOption;
  emit("submit", {
    personId: person.id,
    personUsername: person.username,
    permissionIdList: [...form.permissionIdList],
    startTime: form.startTime,
    expireTime: form.expireTime,
    grantReason: form.grantReason.trim(),
  });
}
</script>

<style scoped>
.grant-form :deep(.el-form-item__label) {
  font-weight: 500;
  color: #334155;
}

.grant-field {
  margin-bottom: 18px;
}

.grant-field-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.grant-field-label {
  font-size: 12.5px;
  font-weight: 500;
  color: #334155;
}

.grant-field-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.link-btn {
  padding: 0;
  border: none;
  background: none;
  font-size: 12px;
  color: var(--admin-primary, #4f46e5);
  cursor: pointer;
}

.link-btn:hover {
  text-decoration: underline;
}

.link-muted {
  color: #94a3b8;
}

/* ==================== 被授权人选择 ==================== */

.person-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.person-search {
  flex: 1;
}

.person-list {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  max-height: 200px;
  overflow-y: auto;
  transition: border-color 0.2s ease;
}

.person-list.field-error,
.perm-box.field-error {
  border-color: #f56c6c;
}

.person-option {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-bottom: 1px solid #f1f5f9;
  cursor: pointer;
  transition: background 0.15s ease;
}

.person-option:last-child {
  border-bottom: none;
}

.person-option:hover {
  background: #f8fafc;
}

.person-option.active {
  background: var(--admin-primary-soft, #eef2ff);
}

.person-radio {
  flex-shrink: 0;
  accent-color: #4f46e5;
}

.person-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

.person-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.person-name {
  font-size: 13px;
  color: #334155;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.person-account {
  font-size: 12px;
  color: #94a3b8;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.person-type {
  flex-shrink: 0;
  padding: 1px 6px;
  border-radius: 999px;
  background: #f1f5f9;
  font-size: 10px;
  color: #64748b;
}

.picker-empty {
  padding: 24px 0;
  text-align: center;
  font-size: 12.5px;
  color: #94a3b8;
}

/* ==================== 权限选择 ==================== */

.perm-box {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  max-height: 220px;
  overflow-y: auto;
  padding: 12px;
  transition: border-color 0.2s ease;
}

.perm-groups {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.perm-group-name {
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 500;
  color: #64748b;
}

.perm-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.perm-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 12px;
  color: #475569;
  cursor: pointer;
  transition: all 0.15s ease;
}

.perm-pill:hover {
  background: #f8fafc;
}

.perm-pill.checked {
  border-color: #c7d2fe;
  background: var(--admin-primary-soft, #eef2ff);
  color: #4338ca;
}

.perm-checkbox {
  width: 13px;
  height: 13px;
  accent-color: #4f46e5;
}

.perm-pill-name {
  font-weight: 500;
}

.perm-pill-code {
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 10px;
  color: #94a3b8;
}

.perm-pill.checked .perm-pill-code {
  color: #6366f1;
}

.field-error-text {
  margin-top: 4px;
  font-size: 12px;
  color: #f56c6c;
}

/* ==================== 时间行 ==================== */

.grant-time-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.grant-time-row :deep(.el-form-item) {
  margin-bottom: 18px;
}
</style>
