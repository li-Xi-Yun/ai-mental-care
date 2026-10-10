/**
 * WebSocket 一次性 ticket
 *
 * 后端接口为 GET /api/get-ws-ticket（注意：真实路径自带 /api 前缀，而前端 axios 的
 * baseURL=/api 且 vite 代理会把 /api 前缀 rewrite 掉，因此这里必须用 fetch 直连后端 origin）。
 * ticket 10s 过期、使用即删，每次建立 WebSocket 连接前都必须重新获取。
 */
import { getAuthHeaders } from "@shared/api/auth";
import type { Result } from "@shared/api/types";
import { resolveBackendOrigin, WS_TICKET_PATH } from "@shared/ws/endpoints";

/** 获取一次性 wsTicket（失败抛错） */
export async function fetchWsTicket(): Promise<string> {
  const res = await fetch(`${resolveBackendOrigin()}${WS_TICKET_PATH}`, {
    method: "GET",
    headers: { ...getAuthHeaders("user") },
  });
  if (!res.ok) {
    throw new Error(`获取 WebSocket ticket 失败：HTTP ${res.status}`);
  }
  const body = (await res.json()) as Result<string>;
  if (body.code !== 200 || !body.data) {
    throw new Error(body.msg || "获取 WebSocket ticket 失败");
  }
  return String(body.data);
}
