import type { AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from "axios";
import NProgress from "nprogress";
import "nprogress/nprogress.css";
import { SUCCESS_CODE } from "./config";
import type { Result } from "./types";
import { getToken, getHeaderName, clearTokenInfo, promptUserLogin } from "./auth";
import { parseJSONWithBigInt, processBigIntFields } from "@/shared/utils/bigint";
import { processTimeFields } from "@/shared/utils/time";

NProgress.configure({ showSpinner: false });

/** 后台接口路径前缀：/admin/**（含登录） */
const ADMIN_PATH_PREFIX = "/admin/";

/** 判断请求是否为后台（admin）接口 */
function isAdminRequest(config: { url?: string }): boolean {
  return !!config.url && config.url.startsWith(ADMIN_PATH_PREFIX);
}

/**
 * 从后端登录返回的 headerName + token 组装鉴权头。
 * 后台接口 → admin 凭证；前台接口 → user 凭证。
 */
function attachAuthHeader(config: InternalAxiosRequestConfig) {
  if (!config.headers) return;
  const kind = isAdminRequest(config) ? "admin" : "user";
  const token = getToken(kind);
  if (!token) return;
  const headerName = getHeaderName(kind) || "Authorization";
  config.headers[headerName] = token;
}

/** 判断响应是否为可解析的 JSON 文本（axios 默认已按 content-type 解析，多数情况 data 已是对象） */
function looksLikeResult(data: unknown): boolean {
  return !!data && typeof data === "object" && "code" in (data as Record<string, unknown>);
}

/**
 * 仅对普通对象/数组做请求体字段处理。
 * FormData / Blob / URLSearchParams 等非普通对象会被 processBigIntFields（按可枚举自有键遍历）
 * 破坏（如 FormData 的 entries 不是可枚举自有键 → multipart 内容丢失），必须原样透传。
 */
function isPlainBody(v: unknown): boolean {
  if (!v || typeof v !== "object") return false;
  if (Array.isArray(v)) return true;
  const proto = Object.getPrototypeOf(v);
  return proto === Object.prototype || proto === null;
}

export function setupRequestInterceptors(instance: AxiosInstance) {
  instance.interceptors.request.use(
    (config: InternalAxiosRequestConfig) => {
      NProgress.start();
      attachAuthHeader(config);

      // 发送前：本地时间字符串 → UTC 时间字符串；安全范围数字字符串还原为 number（配合响应保护）
      // 仅处理普通对象/数组：FormData/Blob 等透传，避免 multipart 等请求体被破坏
      if (isPlainBody(config.data)) {
        config.data = processTimeFields(processBigIntFields(config.data, "restore"), "toUtc");
      }
      return config;
    },
    (error) => {
      NProgress.done();
      return Promise.reject(error);
    },
  );
}

export function setupResponseInterceptors(instance: AxiosInstance) {
  instance.interceptors.response.use(
    (response: AxiosResponse<Result>) => {
      NProgress.done();

      // 1) 若 axios 已解析出对象：统一走「递归保护大整数 + UTC → 本地」
      if (looksLikeResult(response.data)) {
        const protectedData = processTimeFields(
          processBigIntFields(response.data, "stringify"),
          "toLocal",
        ) as Result;
        response.data = protectedData;
        const { data } = protectedData;
        if (data?.code !== undefined && data.code !== SUCCESS_CODE) {
          return Promise.reject(new Error((data as Result).msg || "请求失败"));
        }
        return response;
      }

      // 2) 字符串响应：先做 Long 保护再 JSON.parse（避免精度丢失）
      if (typeof response.data === "string") {
        const parsed = parseJSONWithBigInt(response.data) as unknown;
        if (parsed && typeof parsed === "object") {
          const asResult = parsed as Result;
          if (asResult.code !== undefined) {
            if (asResult.code !== SUCCESS_CODE) {
              return Promise.reject(new Error(asResult.msg || "请求失败"));
            }
            response.data = processTimeFields(
              processBigIntFields(parsed, "stringify"),
              "toLocal",
            ) as Result;
            return response;
          }
        }
      }

      return response;
    },
    (error) => {
      NProgress.done();
      if (error.response?.status === 401) {
        // 401 时按请求来源清除对应模块 token
        const url: string | undefined = error.config?.url;
        const isAdmin = !!url && url.startsWith(ADMIN_PATH_PREFIX);
        if (isAdmin) {
          // 后台有独立登录页：硬跳转 /admin/login
          clearTokenInfo("admin");
          window.location.href = "/admin/login";
        } else {
          // 前台登录是弹窗：清除凭证并在当前页面触发登录弹窗，不刷新、不跳转
          clearTokenInfo("user");
          promptUserLogin();
        }
      }
      return Promise.reject(error);
    },
  );
}