import { defineStore } from "pinia";
import { ref } from "vue";
import { CONVERSATION_LIST_WIDTH, EMOTION_PANEL_WIDTH } from "@/shared/api/config";

/** 语音对话输入方式（与后端 inputTypes 对齐，可多选） */
export const VOICE_INPUT_OPTIONS = [
  { value: "AUDIO", label: "语音" },
  { value: "TEXT", label: "文本" },
] as const;

/** 语音对话输出方式（与后端 outputTypes 对齐，可多选；两个独立按钮） */
export const VOICE_OUTPUT_OPTIONS = [
  { value: "AUDIO", label: "语音" },
  { value: "TEXT", label: "文本" },
] as const;

export const useConversationStore = defineStore(
  "portal-conversation",
  () => {
    const currentConversationId = ref("");
    const conversationList = ref<any[]>([]);

    /* ==================== UI 展示状态（持久化，刷新不重置） ==================== */

    /** 左侧会话列表面板展开状态 */
    const showConversationList = ref(true);
    /** 右侧情绪分析面板展开状态（默认收起） */
    const showEmotionPanel = ref(false);
    /** 历史情绪展开项 */
    const expandedAnalysisId = ref<string | null>(null);

    /** 左右面板宽度（px，拖拽可调，刷新持久化） */
    const conversationListWidth = ref(CONVERSATION_LIST_WIDTH);
    const emotionPanelWidth = ref(EMOTION_PANEL_WIDTH);

    /** 语音对话模式偏好（可多选；下次进入默认选中） */
    const voiceInputTypes = ref<string[]>(["AUDIO"]);
    const voiceOutputTypes = ref<string[]>(["TEXT", "AUDIO"]);

    function setCurrentConversation(id: string) {
      currentConversationId.value = id;
    }

    function setConversationList(list: any[]) {
      conversationList.value = list;
    }

    function toggleConversationList() {
      showConversationList.value = !showConversationList.value;
    }

    function toggleEmotionPanel() {
      showEmotionPanel.value = !showEmotionPanel.value;
    }

    function setExpandedAnalysisId(id: string | null) {
      expandedAnalysisId.value = id;
    }

    /** 更新语音对话模式偏好（输入、输出均为非空数组） */
    function setVoiceModes(inputTypes: string[], outputTypes: string[]) {
      if (inputTypes.length) voiceInputTypes.value = [...inputTypes];
      if (outputTypes.length) voiceOutputTypes.value = [...outputTypes];
    }

    return {
      currentConversationId,
      conversationList,
      showConversationList,
      showEmotionPanel,
      expandedAnalysisId,
      voiceInputTypes,
      voiceOutputTypes,
      conversationListWidth,
      emotionPanelWidth,
      setCurrentConversation,
      setConversationList,
      toggleConversationList,
      toggleEmotionPanel,
      setExpandedAnalysisId,
      setVoiceModes,
    };
  },
  {
    // 面板/选中会话等 UI 状态持久化：刷新后保持用户选择
    persist: {
      pick: [
        "currentConversationId",
        "showConversationList",
        "showEmotionPanel",
        "expandedAnalysisId",
        "voiceInputTypes",
        "voiceOutputTypes",
        "conversationListWidth",
        "emotionPanelWidth",
      ],
    },
  },
);
