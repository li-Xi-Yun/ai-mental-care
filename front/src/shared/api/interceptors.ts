import type { AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from "axios";
import NProgress from "nprogress";
import "nprogress/nprogress.css";
import { SUCCESS_CODE } from "./config";
import type { Result } from "./types";
import { getToken, getHeaderName, clearTokenInfo } from "./auth";
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

export function setupRequestInterceptors(instance: AxiosInstance) {
  instance.interceptors.request.use(
    (config: InternalAxiosRequestConfig) => {
      NProgress.start();
      attachAuthHeader(config);

      // 发送前：本地时间字符串 → UTC 时间字符串；安全范围数字字符串还原为 number（配合响应保护）
      if (config.data && typeof config.data === "object") {
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
        // 401 时按请求来源清除对应模块 token，并跳转到对应登录页
        const url: string | undefined = error.config?.url;
        const isAdmin = !!url && url.startsWith(ADMIN_PATH_PREFIX);
        if (isAdmin) {
          clearTokenInfo("admin");
          window.location.href = "/admin/login";
        } else {
          clearTokenInfo("user");
          window.location.href = "/login";
        }
      }
      return Promise.reject(error);
    },
  );
}