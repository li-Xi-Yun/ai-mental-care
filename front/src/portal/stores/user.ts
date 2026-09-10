import { defineStore } from "pinia";
import { ref } from "vue";

export const useUserStore = defineStore(
  "portal-user",
  () => {
    const token = ref("");
    const userInfo = ref<Record<string, any>>({});
    const loginDialogVisible = ref(false);
    const loginDialogTab = ref<"login" | "register">("login");

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

    return {
      token,
      userInfo,
      loginDialogVisible,
      loginDialogTab,
      setToken,
      setUserInfo,
      clearUser,
      openLoginDialog,
      closeLoginDialog,
    };
  },
  {
    persist: {
      pick: ["token"],
    },
  },
);