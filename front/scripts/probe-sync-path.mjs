// 后端 DeepSeek 同步链路探针（无需浏览器，内存占用极小）
// 用途：验证「发一条消息 → 情绪分析/心理评估异步生成」在真实 JVM 下是否可用。
// 凭证：从 scripts/.portal_token.tmp 读取（不回显）。
import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const TOKEN = (process.env.PORTAL_TOKEN || readFileSync(path.join(__dirname, ".portal_token.tmp"), "utf8")).trim();
const BASE = "http://localhost:8088";
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function api(method, path, body) {
  const headers = { "Content-Type": "application/json" };
  if (TOKEN) headers["user-token"] = TOKEN;
  const r = await fetch(`${BASE}${path}`, { method, headers, body: body ? JSON.stringify(body) : undefined });
  return { status: r.status, text: await r.text() };
}

if (!TOKEN) throw new Error("PORTAL_TOKEN required");
const init = await api("POST", "/user/conversation/lifecycle/init", { inputTypes: ["TEXT"], outputTypes: ["TEXT"] });
const cid = init.text.match(/"conversationId"\s*:\s*"?(\d+)/)?.[1];
if (!cid) throw new Error("init failed: " + init.text.slice(0, 200));

const send = await api("POST", "/user/conversation/adapter/text/send", {
  message: "我最近很焦虑，晚上睡不好，白天也很累，我该怎么办？",
  conversationId: cid,
});

console.log("conversationId=" + cid);
console.log("send http=" + send.status + " body=" + send.text.slice(0, 300));

await sleep(30_000); // 等异步情绪分析 + 评估图跑完（含重试）

const emo = await api("POST", `/user/conversation/emotion-analysis/list/${cid}`, { pageNum: 1, pageSize: 10 });
const diag = await api("POST", `/user/conversation/emotion-diagnosis/list/${cid}`, { pageNum: 1, pageSize: 1 });
const emoTotal = Number(emo.text.match(/"total"\s*:\s*(\d+)/)?.[1] ?? 0);
const diagTotal = Number(diag.text.match(/"total"\s*:\s*(\d+)/)?.[1] ?? 0);
console.log(`emo-analysis total=${emoTotal} http=${emo.status}`);
console.log(`diagnosis total=${diagTotal} http=${diag.status}`);
console.log(JSON.stringify({ cid, sendOk: send.status === 200, emoTotal, diagTotal }));
