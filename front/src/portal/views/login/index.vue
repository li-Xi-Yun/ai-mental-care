<template>
  <!-- 登录占位页：登录主体是弹窗（LoginDialog），本页仅负责在挂载后自动弹出登录弹窗，
       并在弹窗关闭/登录成功后回到用户访问 /login 前所在的业务页（不刷新、停留原页） -->
  <div class="portal-login-shell">
    <el-empty description="正在打开登录窗口…">
      <el-button type="primary" @click="userStore.openLoginDialog('login')">打开登录窗口</el-button>
    </el-empty>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onBeforeUnmount, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useUserStore } from "@/portal/stores/user";

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

/** 回到的来源页：优先取 query 里的 from（如 /login?from=/conversation），否则回首页 */
let returnTo = "/";
const fromQuery = route.query.from;
// 排除 /login 自身，避免 from=/login 时 replace 回本页造成停留/循环
if (typeof fromQuery === "string" && fromQuery.startsWith("/") && !fromQuery.startsWith("/login")) {
  returnTo = fromQuery;
} else {
  // 站内进入 /login 时，history.state.back 指向来源页（仅站内可靠）
  const back = (window.history.state?.back as string | null) ?? null;
  if (back && back.startsWith("/") && !back.startsWith("/login")) {
    returnTo = back;
  }
}

/** 登录弹窗关闭（登录成功 closeLoginDialog 或手动关闭）后，回到来源业务页 */
let unlocked = false;
function unlockAndReturn() {
  if (unlocked) return;
  unlocked = true;
  router.replace(returnTo);
}

watch(
  () => userStore.loginDialogVisible,
  (visible) => {
    if (!visible) unlockAndReturn();
  },
);

onMounted(() => {
  // 挂载后自动弹出登录弹窗
  userStore.openLoginDialog("login");
});

onBeforeUnmount(() => {
  unlockAndReturn();
});
</script>

<style scoped>
.portal-login-shell {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 52vh;
}
</style>