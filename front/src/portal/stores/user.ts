import { defineStore } from "pinia";
import { ref } from "vue";

export const useUserStore = defineStore(
  "portal-user",
  () => {
    const token = ref("");
    const userInfo = ref<Record<string, any>>({});
    const loginDialogVisible = ref(false);
    const loginDialogTab = ref<"login" | "register">("login");
    const loginSuccessVersion = ref(0);

    function setToken(val: string) {
      token.value = val;
    }

    function setUserInfo(info: Record<string, any>) {
      userInfo.value = info;
    }

    function clearUser() {
      token.value = "";
      userInfo.value = {};
    }

    function openLoginDialog(tab: "login" | "register" = "login") {
      loginDialogTab.value = tab;
      loginDialogVisible.value = true;
    }

    function closeLoginDialog() {
      loginDialogVisible.value = false;
    }

    function consumeLoginSuccess(): void {
      loginSuccessVersion.value += 1;
    }

    return {
      token,
      userInfo,
      loginDialogVisible,
      loginDialogTab,
      loginSuccessVersion,
      setToken,
      setUserInfo,
      clearUser,
      openLoginDialog,
      closeLoginDialog,
      consumeLoginSuccess,
    };
  },
  {
    persist: {
      pick: ["token"],
    },
  },
);