import { ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import type { ConversationItem } from "@/portal/api/conversation/types";
import {
  getConversationList,
  updateConversationName,
  deleteConversation,
} from "@/portal/api/conversation/conversation";
import { releaseConversation } from "@/portal/composables/useConversationLifecycle";

interface ConversationListApiResponse {
  data?: {
    data?: { records?: unknown[] } | unknown[];
  };
}

function unwrapList(res: ConversationListApiResponse): unknown[] {
  const body = res?.data?.data;
  if (Array.isArray(body)) return body;
  if (body && typeof body === "object" && "records" in body && Array.isArray(body.records)) return body.records;
  return [];
}

export function useConversationList() {
  const conversationList = ref<ConversationItem[]>([]);
  const currentConversationId = ref<string>("");

  async function loadConversationList(selectFirst: boolean) {
    try {
      const res = await getConversationList({ pageNum: 1, pageSize: 100 });
      const list = unwrapList(res).map((c: any): ConversationItem => ({
        id: String(c.id),
        name: String(c.name ?? "新对话"),
        currentRound: Number(c.currentRound ?? 0),
        lastActiveTime: c.lastActiveTime ?? c.createdTime ?? "",
        draft: false,
      }));
      conversationList.value = list;

      if (selectFirst) {
        if (!currentConversationId.value || !list.some((c) => c.id === currentConversationId.value)) {
          currentConversationId.value = list[0]?.id ?? "";
        }
      } else if (currentConversationId.value && !list.some((c) => c.id === currentConversationId.value)) {
        currentConversationId.value = list[0]?.id ?? "";
      }
      return list;
    } catch {
      throw new Error("会话列表加载失败");
    }
  }

  async function handleRename(item: ConversationItem): Promise<boolean> {
    if (item.draft) return false;
    let newName = "";
    try {
      const { value } = await ElMessageBox.prompt("请输入新的会话名称", "重命名会话", {
        confirmButtonText: "保存",
        cancelButtonText: "取消",
        inputValue: item.name,
        inputValidator: (v: string) => (v && v.trim() ? true : "名称不能为空"),
      });
      newName = value.trim();
    } catch {
      return false;
    }
    try {
      await updateConversationName(item.id, newName);
      item.name = newName;
      ElMessage.success("会话已重命名");
      return true;
    } catch {
      ElMessage.error("重命名失败，请稍后重试");
      return false;
    }
  }

  async function handleDelete(item: ConversationItem): Promise<boolean> {
    if (item.draft) {
      try {
        await deleteConversation(item.id);
        releaseConversationLifecycle(item.id);
        removeLocally(item.id);
      } catch {
        ElMessage.error("删除失败，请稍后重试");
        return false;
      }
      return true;
    }
    try {
      await ElMessageBox.confirm("删除后该会话及关联数据不可恢复，确定删除吗？", "删除会话", {
        type: "warning",
        confirmButtonText: "删除",
        cancelButtonText: "取消",
      });
    } catch {
      return false;
    }
    try {
      await deleteConversation(item.id);
      releaseConversationLifecycle(item.id);
      removeLocally(item.id);
      ElMessage.success("会话已删除");
      return true;
    } catch {
      ElMessage.error("删除失败，请稍后重试");
      return false;
    }
  }

  function releaseConversationLifecycle(conversationId: string) {
    void releaseConversation(conversationId);
  }

  function removeLocally(id: string) {
    conversationList.value = conversationList.value.filter((c) => c.id !== id);
  }

  return {
    conversationList,
    currentConversationId,
    loadConversationList,
    handleRename,
    handleDelete,
    removeLocally,
    releaseConversationLifecycle,
  };
}