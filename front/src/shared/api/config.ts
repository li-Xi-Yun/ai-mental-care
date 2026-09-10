/**
 * 基础层 - API 全局配置
 *
 * 统一管理后端服务地址、路径前缀、超时、Mock 开关等基础信息。
 * 所有具体业务模块的 API 都基于此处定义的前缀拼接请求地址。
 */

/**
 * 后端服务统一根地址（统一请求前缀）。
 * - 接口请求：httpClient 拼接为 `${API_BASE_URL}${path}`。
 * - 图片等静态资源：相对路径经 normalizeResourceUrl 携带此前缀发送；完整 URL 则直接请求，不拼接。
 * 开发环境使用空字符串（相对路径），由 Vite 代理转发到 http://localhost:8088。
 * 生产环境按实际部署地址配置（如 `https://api.example.com`，由 NGINX 转发到后端）。
 */
export const API_BASE_URL = "";

/** 前台用户接口统一前缀 */
export const USER_API_PREFIX = "";
/** 后台管理接口统一前缀 */
export const ADMIN_API_PREFIX = "";

/** 默认请求超时时间（毫秒） */
export const DEFAULT_TIMEOUT = 15000;

/**
 * Mock 开关：true 时走本地数据模拟，不发送真实请求。
 * 后端服务可用时（文档提供的测试账号可用），保持 false 以接入真实接口。
 */
export const USE_MOCK = false;

/** 业务成功的状态码 */
export const SUCCESS_CODE = 200;

/* ==================== 品牌与平台信息 ==================== */

/** 平台名称 */
export const PLATFORM_NAME = "AI Mental Care";
/** 平台副标题 */
export const PLATFORM_SUBTITLE = "AI 心理健康关怀平台";
/** 平台标语 */
export const PLATFORM_SLOGAN = "倾听你的心声，守护你的心灵";
/** 管理端名称 */
export const ADMIN_PLATFORM_NAME = "AI Mental Care 管理系统";
/** 管理端标题 */
export const ADMIN_PLATFORM_TITLE = "管理后台";

/* ==================== 主题配色 ==================== */

/** Portal 主色（紫蓝色调） */
export const PORTAL_PRIMARY_COLOR = "#6c63ff";
/** Portal 深色 */
export const PORTAL_DARK_COLOR = "#3f3d9e";
/** Portal 浅底色 */
export const PORTAL_LIGHT_BG = "#f0eeff";
/** Portal 浅色 */
export const PORTAL_LIGHT_COLOR = "#e8e6ff";

/** Admin 主色（红色调） */
export const ADMIN_PRIMARY_COLOR = "#ff6b6b";
/** Admin 深色 */
export const ADMIN_DARK_COLOR = "#c44569";
/** Admin 侧边栏背景色 */
export const ADMIN_SIDEBAR_BG = "#1a1a2e";

/** 功能色 */
export const COLOR_SUCCESS = "#52c41a";
export const COLOR_WARNING = "#fa8c16";
export const COLOR_DANGER = "#ff4d4f";
export const COLOR_INFO = "#1890ff";

/* ==================== 布局尺寸 ==================== */

/** Portal 顶栏高度（px） */
export const PORTAL_HEADER_HEIGHT = 60;
/** Admin 侧边栏宽度（px） */
export const ADMIN_SIDEBAR_WIDTH = 220;
/** Admin 顶栏高度（px） */
export const ADMIN_HEADER_HEIGHT = 56;

/** AI 对话页 - 左栏（会话列表）宽度（px） */
export const CONVERSATION_LIST_WIDTH = 220;
/** AI 对话页 - 右栏（情绪分析面板）宽度（px） */
export const EMOTION_PANEL_WIDTH = 260;

/* ==================== 分页默认值 ==================== */

/** 默认每页条数 */
export const DEFAULT_PAGE_SIZE = 10;
/** 分页器布局 */
export const PAGINATION_LAYOUT = "total, prev, pager, next";

/* ==================== 首页配置 ==================== */

/** 首页功能卡片 */
export const HOME_FEATURE_CARDS = [
  {
    icon: "💬",
    title: "AI 智能对话",
    description: "与AI心理助手畅聊，获取情绪分析与支持",
    route: "/conversation",
    action: "开始对话",
  },
  {
    icon: "📋",
    title: "量表测评",
    description: "专业心理量表自评，了解心理状态",
    route: "/scale",
    action: "开始测评",
  },
  {
    icon: "🧠",
    title: "心理诊断",
    description: "AI生成诊断报告，全面评估心理状况",
    route: "/diagnosis",
    action: "查看诊断",
  },
] as const;

/** 首页公告列表 */
export const HOME_ANNOUNCEMENTS = [
  "新增SCL-90症状自评量表，欢迎体验",
  "AI对话支持语音输入，更便捷地倾诉心声",
  "心理诊断报告支持反馈评分，帮助我们改进",
];

/* ==================== 量表测评配置 ==================== */

/** 量表分类标签 */
export const SCALE_CATEGORIES = [
  { label: "全部", value: "all" },
  { label: "焦虑评估", value: "anxiety" },
  { label: "抑郁评估", value: "depression" },
  { label: "压力评估", value: "stress" },
  { label: "睡眠评估", value: "sleep" },
] as const;

/* ==================== 心理诊断配置 ==================== */

/** 诊断筛选标签 */
export const DIAGNOSIS_FILTERS = [
  { label: "全部", value: "all" },
  { label: "需关注", value: "attention" },
  { label: "正常", value: "normal" },
] as const;

/* ==================== 情绪标签配置 ==================== */

/** 情绪类型及对应颜色 */
export const EMOTION_COLORS: Record<string, string> = {
  平静: "#6c63ff",
  焦虑: "#ff6b6b",
  开心: "#52c41a",
  低落: "#fa8c16",
  紧张: "#1890ff",
  愤怒: "#ff4d4f",
};

/* ==================== 导航菜单配置 ==================== */

/** Portal 导航菜单 */
export const PORTAL_NAV_ITEMS = [
  { label: "首页", route: "/" },
  { label: "AI对话", route: "/conversation" },
  { label: "心理诊断", route: "/diagnosis" },
  { label: "量表测评", route: "/scale" },
  { label: "个人中心", route: "/profile" },
] as const;

/** Admin 侧边栏菜单 */
export const ADMIN_NAV_ITEMS = [
  { label: "用户管理", route: "/admin/user", icon: "User" },
  { label: "管理员管理", route: "/admin/admin", icon: "UserFilled" },
  { label: "文件管理", route: "/admin/file", icon: "Document" },
  { label: "量表管理", route: "/admin/scale", icon: "DataAnalysis" },
  { label: "症状字典", route: "/admin/symptom", icon: "FirstAidKit" },
  { label: "AI节点配置", route: "/admin/ai-node", icon: "SetUp" },
  { label: "Skill管理", route: "/admin/skill", icon: "MagicStick" },
  { label: "个人资料", route: "/admin/profile", icon: "User" },
] as const;

/* ==================== 个人中心菜单配置 ==================== */

/** 个人中心侧边菜单 */
export const PROFILE_MENU_ITEMS = [
  { key: "info", label: "基本信息" },
  { key: "password", label: "修改密码" },
  { key: "report", label: "健康报告" },
  { key: "settings", label: "偏好设置" },
] as const;

/* ==================== 文件管理配置 ==================== */

/** 文件分类选项 */
export const FILE_CATEGORIES = [
  { label: "资料", value: "material" },
  { label: "图表", value: "chart" },
  { label: "数据", value: "data" },
  { label: "音频", value: "audio" },
  { label: "默认分类", value: "default" },
] as const;

/** 文件类型选项 */
export const FILE_TYPES = [
  { label: "PDF", value: "pdf" },
  { label: "PNG", value: "png" },
  { label: "Excel", value: "xlsx" },
  { label: "Word", value: "docx" },
  { label: "MP3", value: "mp3" },
] as const;

/* ==================== 症状字典配置 ==================== */

/** 症状严重等级最大值 */
export const SYMPTOM_MAX_SEVERITY = 5;

/* ==================== AI节点配置 ==================== */

/** AI节点分组选项 */
export const AI_NODE_GROUPS = [
  { label: "诊断", value: "diagnosis" },
  { label: "评估", value: "assessment" },
  { label: "映射", value: "mapping" },
] as const;

/** AI模型类型选项 */
export const AI_MODEL_TYPES = [
  { label: "GPT-4o", value: "gpt-4o" },
  { label: "Claude-3", value: "claude-3" },
  { label: "GLM-4", value: "glm-4" },
] as const;