import { reactive, ref, type Ref } from "vue";
import { getEmotionAnalysisList, type EmotionAnalysisVO } from "@/portal/api/conversation/emotion-analysis";
import { DEFAULT_PAGE_SIZE } from "@/shared/api/config";

/** 情绪分析列表面板数据（后端 VO 字段；每个 item 就是一轮的情绪分析） */
export interface EmotionAnalysisItem {
  /** 分析ID（雪花 Long → 字符串，避免精度丢失） */
  id: string;
  /** 轮次 */
  roundNum: number;
  /** 情感分析详情 */
  analysisContent?: string;
  /** 情感标签（后端枚举：anger/neutral/happy/...） */
  emotionLabel?: string;
  /** 情感细分标签 */
  emotionSubLabel?: string;
  /** 情感识别置信度（0-1） */
  emotionConfidence?: number;
  /** 情绪强度（0-1） */
  emotionIntensity?: number;
  /** 较上一轮的情绪变化趋势 */
  emotionTrend?: string;
  /** PAD 愉悦度 P [‑1,1] */
  pScore?: number;
  /** PAD 唤醒度 A [‑1,1] */
  aScore?: number;
  /** PAD 支配度 D [‑1,1] */
  dScore?: number;
  /** 负向情绪占比（0-1） */
  negativeEmotionRatio?: number;
  /** 中性情绪占比（0-1） */
  neutralEmotionRatio?: number;
  /** 正向情绪占比（0-1） */
  positiveEmotionRatio?: number;
  /** 创建时间（时间戳数字或时间字符串，dayjs 均可解析） */
  createdTime?: number | string;
}

/** 列表分页加载状态 */
export interface EmotionPagination {
  pageNum: number;
  pageSize: number;
  total: number;
  /** 是否还有下一页 */
  hasMore: boolean;
  /** 当前是否正在加载 */
  loading: boolean;
  /** 当前是否正在加载更多（追加模式） */
  loadingMore: boolean;
  /** 分页查询失败标记 */
  loadError: boolean;
}

export interface UseEmotionListResult {
  /** 全部已加载条目（新轮在前，与后端 created_time 倒序对齐；Ref 供调用方读写） */
  items: Ref<EmotionAnalysisItem[]>;
  /** 分页状态 */
  pagination: EmotionPagination;
  /** 加载首页数据 */
  load: (conversationId: string) => Promise<void>;
  /** 追加下一页 */
  loadMore: () => Promise<void>;
  /** 清空并重置（切换/删除会话时调用） */
  reset: () => void;
}

/** PAD 字段兼容：后端历史版本的 VO 可能是小写 pscore/ascore/dscore */
function padValue(vo: EmotionAnalysisVO, key: "pScore" | "aScore" | "dScore"): number | undefined {
  const camel = vo[key];
  if (camel != null) return camel;
  const lower = (vo as unknown as Record<string, number | undefined>)[key.toLowerCase()];
  return lower ?? undefined;
}

/**
 * 情绪分析列表面板数据加载（职责单一：只负责按会话分页拉取后端 `emotion_analysis` 数据）。
 * - 设计稿 3-conversation.html 为「每轮一条、按轮倒序」的全量列表 → 首屏 pageSize=DEFAULT_PAGE_SIZE，滚动加载更多。
 * - 后端接口 POST /user/conversation/emotion-analysis/list/{conversationId} 返回 PageResult（records + total）。
 */
export function useEmotionList(conversationIdRef: () => string): UseEmotionListResult {
  const items = ref<EmotionAnalysisItem[]>([]);
  const pagination = reactive<EmotionPagination>({
    pageNum: 1,
    pageSize: DEFAULT_PAGE_SIZE,
    total: 0,
    hasMore: false,
    loading: false,
    loadingMore: false,
    loadError: false,
  });

  /** 后端 VO → 前端 item（字段名对齐，createdTime 原样透传供 dayjs 解析） */
  function mapVO(vo: EmotionAnalysisVO): EmotionAnalysisItem {
    return {
      id: String(vo.id),
      roundNum: Number(vo.roundNum ?? 0),
      analysisContent: vo.analysisContent ?? "",
      emotionLabel: vo.emotionLabel ?? "",
      emotionSubLabel: vo.emotionSubLabel ?? "",
      emotionConfidence: vo.emotionConfidence ?? undefined,
      emotionIntensity: vo.emotionIntensity ?? undefined,
      emotionTrend: vo.emotionTrend ?? "",
      pScore: padValue(vo, "pScore"),
      aScore: padValue(vo, "aScore"),
      dScore: padValue(vo, "dScore"),
      negativeEmotionRatio: vo.negativeEmotionRatio ?? undefined,
      neutralEmotionRatio: vo.neutralEmotionRatio ?? undefined,
      positiveEmotionRatio: vo.positiveEmotionRatio ?? undefined,
      createdTime: vo.createdTime ?? undefined,
    };
  }

  /** 从分页响应中提取 records 列表（兼容数组 / {records, total} 两种形态） */
  function unwrapPage(payload: unknown): { records: EmotionAnalysisVO[]; total: number } {
    const body = payload as
      | { records?: EmotionAnalysisVO[]; total?: number }
      | EmotionAnalysisVO[]
      | null;
    if (Array.isArray(body)) return { records: body, total: body.length };
    if (body && Array.isArray(body.records)) {
      return { records: body.records, total: Number(body.total ?? body.records.length) };
    }
    return { records: [], total: 0 };
  }

  /** 请求某一页，返回 VO 列表 */
  async function fetchPage(
    conversationId: string,
    pageNum: number,
    pageSize: number,
  ): Promise<{ records: EmotionAnalysisVO[]; total: number }> {
    const res = await getEmotionAnalysisList(conversationId, { pageNum, pageSize });
    return unwrapPage(res?.data?.data);
  }

  /** 刷新首屏：按会话拉取第 1 页并重置分页状态 */
  async function load(conversationId: string): Promise<void> {
    pagination.loading = true;
    pagination.loadError = false;
    try {
      const { records, total } = await fetchPage(conversationId, 1, pagination.pageSize);
      items.value = records.map(mapVO);
      pagination.total = total;
      pagination.hasMore = items.value.length < total;
      pagination.pageNum = 1;
    } catch {
      items.value = [];
      pagination.loadError = true;
    } finally {
      pagination.loading = false;
    }
  }

  /** 追加下一页（滚动加载更多） */
  async function loadMore(): Promise<void> {
    if (pagination.loading || pagination.loadingMore || !pagination.hasMore) return;
    const conversationId = conversationIdRef();
    if (!conversationId) return;
    pagination.loadingMore = true;
    try {
      const { records } = await fetchPage(conversationId, pagination.pageNum + 1, pagination.pageSize);
      // 追加并去重（按 id）
      const existingIds = new Set(items.value.map((i) => i.id));
      const fresh = records.map(mapVO).filter((i) => !existingIds.has(i.id));
      items.value = [...items.value, ...fresh];
      pagination.pageNum += 1;
      pagination.hasMore = items.value.length < pagination.total;
    } catch {
      /* 保留已有数据，下次滚动再试 */
    } finally {
      pagination.loadingMore = false;
    }
  }

  /** 清空当前数据并重置分页（切换/删除会话时调用） */
  function reset(): void {
    items.value = [];
    pagination.pageNum = 1;
    pagination.total = 0;
    pagination.hasMore = false;
    pagination.loading = false;
    pagination.loadingMore = false;
    pagination.loadError = false;
  }

  return { items, pagination, load, loadMore, reset };
}