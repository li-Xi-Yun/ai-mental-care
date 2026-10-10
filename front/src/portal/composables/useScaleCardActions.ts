/**
 * 量表推荐卡片动作（开始作答 / 已完成 / 放弃）——文本对话页与语音对话页共用。
 * 卡片本身由 PendingActionCard.vue 渲染，本 composable 只负责动作与提交。
 */
import { reactive } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { completeToolAnswer } from "@/portal/api/scale/user-scale";
import type { PendingActionItem } from "@/portal/api/conversation/types";

export function useScaleCardActions(getConversationId: () => string) {
  const router = useRouter();
  /** 各卡片正在执行的动作（用于按钮 loading） */
  const pendingActActions = reactive<Record<string, "start" | "done" | undefined>>({});

  /** 开始作答：跳转到量表答题页，携带 toolId 与 conversationId 供答题页回写记录ID与提交完成 */
  function handleScaleStart(act: PendingActionItem) {
    const data = act.actionData;
    if (!data?.scaleId) {
      ElMessage.warning("量表信息缺失，无法开始作答");
      return;
    }
    pendingActActions[act.id] = "start";
    router.push(`/scale/${data.scaleId}/answer?toolId=${act.id}&conversationId=${getConversationId()}`);
  }

  /** 用户上报作答完成 / 放弃，触发后端分析与状态流转 */
  async function handleScaleAnswered(act: PendingActionItem, answered: number) {
    if (answered === 1) {
      try {
        await ElMessageBox.confirm("确认已完成该量表全部作答？提交后 AI 将分析结果并回复你。", "提交测评", {
          confirmButtonText: "已全部答完",
          cancelButtonText: "再检查一下",
          type: "info",
        });
      } catch {
        return;
      }
    } else {
      try {
        await ElMessageBox.confirm("确定放弃本次量表吗？", "放弃测评", { type: "warning" });
      } catch {
        return;
      }
    }
    try {
      pendingActActions[act.id] = "done";
      // 雪花 ID 一律字符串直传（toolId/conversationId 均为 19 位）
      await completeToolAnswer({
        toolId: act.id,
        conversationId: getConversationId(),
        answered,
        recordId: act.actionData?.recordId,
      });
      ElMessage.success(answered === 1 ? "测评已提交，AI 正在分析…" : "已取消本次测评");
    } catch (e: any) {
      ElMessage.error(e?.message || "提交失败，请稍后重试");
    } finally {
      delete pendingActActions[act.id];
    }
  }

  return { pendingActActions, handleScaleStart, handleScaleAnswered };
}
