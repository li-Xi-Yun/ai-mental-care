/**
 * 管理员 WebSocket 一次性 ticket
 *
 * 管理端实时通知（文件向量解析状态等）建立 STOMP 连接前调用。
 * 后端接口为 GET /admin/get-ws-ticket（真实路径自带 /admin 前缀），
 * 使用 admin-token 请求；ticket 10s 过期、使用即删，每次连接都必须重新获取。
 */
import { getAuthHeaders } from "@shared/api/auth";
import type { Result } from "@shared/api/types";
import { ADMIN_WS_TICKET_PATH, resolveBackendOrigin } from "@shared/ws/endpoints";

/** 获取管理员一次性 wsTicket（失败抛错） */
export async function fetchAdminWsTicket(): Promise<string> {
  const res = await fetch(`${resolveBackendOrigin()}${ADMIN_WS_TICKET_PATH}`, {
    method: "GET",
    headers: { ...getAuthHeaders("admin") },
  });
  if (!res.ok) {
    throw new Error(`获取管理员 WebSocket ticket 失败：HTTP ${res.status}`);
  }
  const body = (await res.json()) as Result<string>;
  if (body.code !== 200 || !body.data) {
    throw new Error(body.msg || "获取管理员 WebSocket ticket 失败");
  }
  return String(body.data);
}
