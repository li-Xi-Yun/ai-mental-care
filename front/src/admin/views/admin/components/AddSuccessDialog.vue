<template>
  <el-dialog
    :model-value="modelValue"
    title="新增管理员成功"
    width="480px"
    align-center
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="add-success-body">
      <div class="success-icon">
        <el-icon :size="28"><CircleCheckFilled /></el-icon>
      </div>
      <p class="success-title">管理员「{{ data?.username || "—" }}」创建成功</p>
      <p class="success-tip">
        登录账号由系统自动生成，仅本次展示，请<strong>安全转交</strong>该管理员本人保存。
      </p>

      <div class="account-box">
        <span class="account-label">登录账号</span>
        <span class="account-value" data-testid="created-login-account">{{ data?.loginAccount || "—" }}</span>
        <el-button
          v-if="data?.loginAccount"
          text
          type="primary"
          class="copy-btn"
          :disabled="copied"
          @click="copyAccount"
        >
          <el-icon :size="14" style="margin-right: 4px"><CopyDocument /></el-icon>{{ copied ? "已复制" : "复制" }}
        </el-button>
      </div>

      <p v-if="usedDefaultPwd" class="default-pwd-tip">
        该账号当前仍为系统默认密码，请提醒管理员首次登录后立即修改。
      </p>
    </div>

    <template #footer>
      <el-button type="primary" @click="emit('update:modelValue', false)">完 成</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from "vue";
import { ElMessage } from "element-plus";
import { CircleCheckFilled, CopyDocument } from "@element-plus/icons-vue";
import type { AdminAddVO } from "@/admin/api/admin/admin-manage";

/**
 * 新增管理员成功弹窗：一次性展示系统生成的登录账号，供创建者安全转交新管理员。
 * 账号仅在本次创建响应中出现，关闭后不再展示。
 */
const props = defineProps<{
  modelValue: boolean;
  data?: AdminAddVO | null;
  /** 是否使用了系统默认密码（用于提示首次登录修改） */
  usedDefaultPwd?: boolean;
}>();
const emit = defineEmits<{
  (e: "update:modelValue", v: boolean): void;
}>();

const copied = ref(false);
watch(
  () => props.modelValue,
  (v) => {
    if (v) copied.value = false;
  },
);

async function copyAccount() {
  if (!props.data?.loginAccount) return;
  const text = props.data.loginAccount;
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text);
    } else {
      // 非安全上下文兜底（如局域网 http 访问）
      const ta = document.createElement("textarea");
      ta.value = text;
      ta.style.position = "fixed";
      ta.style.opacity = "0";
      document.body.appendChild(ta);
      ta.select();
      document.execCommand("copy");
      document.body.removeChild(ta);
    }
    copied.value = true;
    ElMessage.success("账号已复制，请安全转交");
  } catch {
    ElMessage.error("复制失败，请手动抄录");
  }
}
</script>

<style scoped>
.add-success-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  text-align: center;
}

.success-icon {
  color: #10b981;
}

.success-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #0f172a;
}

.success-tip {
  margin: 0;
  font-size: 13px;
  color: #64748b;
  line-height: 1.6;
}

.account-box {
  width: 100%;
  margin-top: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px dashed #cbd5e1;
}

.account-label {
  font-size: 12px;
  color: #94a3b8;
}

.account-value {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 1px;
  color: #1e293b;
}

.copy-btn {
  flex-shrink: 0;
}

.default-pwd-tip {
  margin: 4px 0 0;
  font-size: 12.5px;
  color: #b45309;
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 8px;
  padding: 8px 12px;
  width: 100%;
}
</style>
