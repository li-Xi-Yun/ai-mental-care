import { defineStore } from "pinia";
import { ref } from "vue";

export const useConversationStore = defineStore("portal-conversation", () => {
  const currentConversationId = ref("");
  const conversationList = ref<any[]>([]);

  function setCurrentConversation(id: string) {
    currentConversationId.value = id;
  }

  function setConversationList(list: any[]) {
    conversationList.value = list;
  }

  return { currentConversationId, conversationList, setCurrentConversation, setConversationList };
});