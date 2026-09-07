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
export const API_BASE_URL = "/api";

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