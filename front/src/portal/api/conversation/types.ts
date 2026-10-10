/**
 * 会话域共享类型（文本对话页 / 语音对话页共用）
 *
 * 注意：所有 ID（会话、消息、待处理交互、量表、记录）均为 19 位雪花 ID，
 * 一律用 string 承载或宽松的 number | string，禁止 Number() 转换（会丢精度）。
 */

/** 会话列表项（含页面本地扩展字段） */
export interface ConversationItem {
  id: string;
  name: string;
  currentRound: number;
  lastActiveTime: string;
  /** 本地新建、服务端尚未产生消息的草稿会话 */
  draft?: boolean;
  /** 会话生命周期（后端 adapter/管道）是否已初始化 */
  initialized?: boolean;
}

/** 对话记忆消息 */
export interface MemoryMessage {
  id: string;
  content: string;
  type: string;
  roundNum: number;
  createdTime: string;
}

/** 量表卡片 actionData（后端已解析为对象；大整数 ID 经安全解析后为 string） */
export interface ScaleCardActionData {
  scaleId?: number | string;
  scaleName?: string;
  totalQuestions?: number;
  recommendReason?: string;
  recordId?: number | string;
}

/** 待处理人工交互（卡片） */
export interface PendingActionItem {
  id: string;
  actionType: number;
  roundNum: number;
  actionData?: ScaleCardActionData;
  status: number;
  expireTime?: string;
  completedTime?: string;
  createdTime?: string;
}

/** 记忆轮次展平结果 */
export interface RoundMemory {
  messages: MemoryMessage[];
  pendingActions: PendingActionItem[];
}

/** 发送用户消息响应（当前轮次与真实会话 ID） */
export interface UserMessageSendVO {
  conversationId?: number | string;
  currentRound?: number;
}
