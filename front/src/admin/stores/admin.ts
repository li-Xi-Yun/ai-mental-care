import { defineStore } from "pinia";
import { ref } from "vue";

export const useAdminStore = defineStore(
  "admin-user",
  () => {
    const token = ref("");
    const adminInfo = ref<Record<string, any>>({});

    function setToken(val: string) {
      token.value = val;
    }

    function setAdminInfo(info: Record<string, any>) {
      adminInfo.value = info;
    }

    function clearAdmin() {
      token.value = "";
      adminInfo.value = {};
    }

    return { token, adminInfo, setToken, setAdminInfo, clearAdmin };
  },
  {
    persist: {
      pick: ["token"],
    },
  },
);