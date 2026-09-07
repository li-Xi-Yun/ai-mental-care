import { defineStore } from "pinia";
import { ref } from "vue";

export const useAdminUserStore = defineStore("admin-user-manage", () => {
  const userList = ref<any[]>([]);
  const total = ref(0);

  function setUserList(list: any[], count: number) {
    userList.value = list;
    total.value = count;
  }

  return { userList, total, setUserList };
});