/**
 * 管理端文件向量解析 WebSocket 推送 E2E 验证
 *
 * 链路：GET /admin/get-ws-ticket(admin-token) → STOMP connect /ws-native →
 *       POST /admin/file/vector/{id}/load → 订阅 /user/queue/file/vector/status/{id}
 *       → 记录收到的状态事件（预期至少 PARSING + FAILED/COMPLETED）
 *
 * 前置：后端 :8088 已启动（Redis/MySQL/Milvus 按 dev 配置），本脚本从 front/ 运行。
 */
import { Client } from "@stomp/stompjs";

const BASE = "http://localhost:8088";
const ADMIN_TOKEN = "eyJhbGciOiJIUzUxMiJ9.eyJlbXBJZCI6IjEifQ.DUYEEMoO08SmPiDkNEgSCZVsRC-o0QwBuG-TFVjwiYLl-OgF7GrmoobrgJCDxZxWHYRr3vAcv2gULVs7ZsodAQ";
const FILE_ID = process.argv[2] ?? "44";

async function fetchAdminWsTicket() {
  const res = await fetch(`${BASE}/admin/get-ws-ticket`, {
    method: "GET",
    headers: { "admin-token": ADMIN_TOKEN },
  });
  if (!res.ok) throw new Error(`ticket HTTP ${res.status}`);
  const body = await res.json();
  if (body.code !== 200 || !body.data) throw new Error(body.msg || "ticket 获取失败");
  return String(body.data);
}

const received = [];
const deadline = Date.now() + 30_000;

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

async function main() {
  // 1. 拿 admin wsTicket
  const ticket = await fetchAdminWsTicket();
  console.log("[1] admin wsTicket 获取成功:", ticket.slice(0, 12), "...");

  // 2. STOMP 连接
  const url = `ws://localhost:8088/ws-native?wsTicket=${encodeURIComponent(ticket)}`;
  const client = new Client({
    webSocketFactory: () => new WebSocket(url),
    connectHeaders: { token: ADMIN_TOKEN },
    reconnectDelay: 0,
    debug: () => {},
  });

  await new Promise((resolve, reject) => {
    client.onConnect = resolve;
    client.onStompError = (f) => reject(new Error("STOMP 错误: " + (f.headers["message"] ?? "")));
    client.onWebSocketClose = () => {};
    client.activate();
  });
  console.log("[2] STOMP 已连接 /ws-native");

  // 3. 订阅状态 topic
  const dest = `/user/queue/file/vector/status/${FILE_ID}`;
  client.subscribe(dest, (msg) => {
    const evt = JSON.parse(msg.body);
    received.push(evt);
    console.log(`[WS] 收到事件 status=${evt.status} progress=${evt.progress} seq=${evt.sequence} msg=${evt.message}`);
  });
  console.log(`[3] 已订阅 ${dest}`);

  // 4. 触发向量化
  const loadResp = await fetch(`${BASE}/admin/file/vector/${FILE_ID}/load`, {
    method: "POST",
    headers: { "admin-token": ADMIN_TOKEN },
  });
  console.log("[4] load 触发 HTTP:", loadResp.status, await loadResp.text());

  // 5. 等待事件
  while (Date.now() < deadline && !received.some((e) => ["COMPLETED", "FAILED", "INTERRUPTED"].includes(e.status))) {
    await sleep(500);
  }

  await sleep(1500); // 再多收一条余量
  console.log("\n=== 结果 ===");
  if (received.length === 0) {
    console.log("❌ 未收到任何 WebSocket 状态事件");
  } else {
    console.log(`✅ 收到 ${received.length} 个事件:`);
    for (const e of received) {
      console.log(`   - ${e.status} (seq=${e.sequence}, progress=${e.progress ?? "-"}, msg=${e.message ?? "-"})`);
    }
    const terminal = received.filter((e) => ["COMPLETED", "FAILED", "INTERRUPTED"].includes(e.status));
    if (terminal.length > 0) {
      console.log(`✅ 含终态事件: ${terminal.map((e) => e.status).join(",")}`);
    } else {
      console.log("⚠️ 未在 30s 内收到终态事件（任务可能仍在解析中）");
    }
  }

  client.deactivate();
  process.exit(0);
}

main().catch((e) => {
  console.error("❌ 脚本失败:", e.message);
  process.exit(1);
});
