import { defineStore } from "pinia";
import { ref } from "vue";

export const useUserStore = defineStore(
  "portal-user",
  () => {
    const token = ref("");
    const userInfo = ref<Record<string, any>>({});

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

    return { token, userInfo, setToken, setUserInfo, clearUser };
  },
  {
    persist: {
      pick: ["token"],
    },
  },
);