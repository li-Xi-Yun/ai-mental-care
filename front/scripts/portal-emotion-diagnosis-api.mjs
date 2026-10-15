// 轻量 API 版：10 轮负面+求助 → 情绪分析 → 轮询评估 → DB 核验（不开浏览器，内存占用极小）
// 凭证：PORTAL_TOKEN / PORTAL_PWD（环境变量），不回显。
import { execFileSync } from "node:child_process";
import { appendFileSync } from "node:fs";

const BASE = "http://localhost:8088";
const ACCOUNT = process.env.PORTAL_ACCOUNT || "1234567890";
let TOKEN = process.env.PORTAL_TOKEN || "";
const PWD = process.env.PORTAL_PWD || "";
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
/** 进度文件：随时可查跑到哪一轮 */
const PROGRESS = process.env.PROGRESS_FILE || "scripts/e2e-progress.log";
const log = (s) => { try { appendFileSync(PROGRESS, `${new Date().toISOString()} ${s}\n`); } catch {} console.log(s); };

const REPLY_TIMEOUT_MS = 120_000;
const DIAGNOSIS_POLL_MS = 15_000;
const DIAGNOSIS_TIMEOUT_MS = 8 * 60_000;

const MESSAGES = [
  "最近总是失眠，躺在床上翻来覆去，白天一点精神都没有。",
  "工作压力特别大，领导天天催进度，我感觉自己快撑不住了。",
  "身边没有人能理解我，什么事都只能自己扛，真的很孤独。",
  "心情一直很低落，对以前喜欢的事情也提不起兴趣了。",
  "我是不是得了抑郁症？为什么会这样，我该怎么办？",
  "每天都觉得很累，帮我分析一下我这是怎么了。",
  "晚上一个人的时候特别难受，正常吗？我该怎么办？",
  "我觉得自己什么都做不好，帮我分析一下为什么会这样。",
  "这种状态已经持续很久了，我是不是需要帮助？",
  "我还是不知道该怎么办，能再帮我分析一下吗？",
];
const EXTRA_MESSAGES = [
  "帮帮我，我真的不知道该怎么办了。",
  "我是不是心理出问题了？能帮我分析一下吗？",
];

function captchaCode(key) {
  return execFileSync("python", ["-c", `import redis,sys
v=redis.Redis(host='127.0.0.1',port=6379,password='132466',db=0,decode_responses=False).get('verification-code:'+sys.argv[1])
print(''.join(chr(b) for b in v[1:-1])+chr(v[-1]&127))`, key], { encoding: "utf8", env: { ...process.env, PYTHONIOENCODING: "utf-8" } }).trim();
}

async function api(method, path, body) {
  const headers = { "Content-Type": "application/json" };
  if (TOKEN) headers["user-token"] = TOKEN;
  const r = await fetch(`${BASE}${path}`, { method, headers, body: body ? JSON.stringify(body) : undefined });
  return { status: r.status, text: await r.text() };
}

const report = { account: ACCOUNT, auth: {}, conversation: {}, rounds: [], diagnosis: {}, db: {}, ok: false };

/* P0 登录 */
if (TOKEN) {
  const c = await api("GET", "/user/profile/profile", null);
  report.auth.tokenValid = c.status === 200 && /"code"\s*:\s*200/.test(c.text);
} else {
  report.auth.tokenValid = false;
}
if (!report.auth.tokenValid) {
  if (!PWD) throw new Error("no token and no PORTAL_PWD");
  const cap = JSON.parse((await api("POST", "/common/verification/code")).text).data;
  const code = captchaCode(cap.key);
  const login = await api("POST", "/user/account/login", { loginAccount: ACCOUNT, password: PWD, key: cap.key, code });
  TOKEN = login.text.match(/"token"\s*:\s*"([^"]+)"/)?.[1] ?? "";
  if (!TOKEN) throw new Error(`login failed http=${login.status}`);
  report.auth.passwordFallback = true;
}

/* P1 建会话 */
const init = await api("POST", "/user/conversation/lifecycle/init", { inputTypes: ["TEXT"], outputTypes: ["TEXT"] });
const cid = init.text.match(/"conversationId"\s*:\s*"?(\d+)/)?.[1];
if (!cid) throw new Error("init failed " + init.text.slice(0, 150));
report.conversation = { conversationId: cid };

/* P2 每条消息：等待 assistant +1 后再发下一条 */
async function assistantCount() {
  const r = await api("POST", `/user/conversation/dialogue/${cid}/memory`, { pageNum: 1, pageSize: 200 });
  return (r.text.match(/"type"\s*:\s*"assistant"/gi) ?? []).length;
}

async function sendAndWait(text) {
  const before = await assistantCount();
  const sent = await api("POST", "/user/conversation/adapter/text/send", { message: text, conversationId: cid });
  const sendOk = sent.status === 200 && /"code"\s*:\s*200/.test(sent.text);
  const started = Date.now();
  let after = before;
  while (Date.now() - started < REPLY_TIMEOUT_MS) {
    await sleep(3000);
    after = await assistantCount();
    if (after > before) break;
  }
  await sleep(2000);
  return { sendOk, replyOk: after > before, assistantCount: after, seconds: Math.round((Date.now() - started) / 1000) };
}

for (let i = 0; i < MESSAGES.length; i++) {
  const r = await sendAndWait(MESSAGES[i]);
  report.rounds.push(r);
  log(`round ${i + 1}/10 replyOk=${r.replyOk} assistant=${r.assistantCount} took=${r.seconds}s`);
}

/* P3 轮询评估 */
async function diagnosisInfo() {
  const r = await api("POST", `/user/conversation/emotion-diagnosis/list/${cid}`, { pageNum: 1, pageSize: 1 });
  const total = Number(r.text.match(/"total"\s*:\s*(\d+)/)?.[1] ?? 0);
  const id = r.text.match(/"records"\s*:\s*\[\s*\{[\s\S]*?"id"\s*:\s*"?(\d+)/)?.[1] ?? "";
  return { total, id };
}
async function poll(deadline) {
  let info = { total: 0, id: "" };
  while (Date.now() < deadline) {
    info = await diagnosisInfo();
    if (info.total > 0) break;
    await sleep(DIAGNOSIS_POLL_MS);
  }
  return info;
}
let diag = await poll(Date.now() + DIAGNOSIS_TIMEOUT_MS);
if (diag.total === 0) {
  for (const m of EXTRA_MESSAGES) { report.rounds.push(await sendAndWait(m)); log("extra round sent"); }
  diag = await poll(Date.now() + 4 * 60_000);
}
report.diagnosis = diag;
log(`diagnosis total=${diag.total} id=${diag.id}`);
const diagnosisId = diag.id;

/* P4 DB 核验 */
if (diagnosisId) {
  try {
    const out = execFileSync("python", ["-c", `import pymysql,sys,json
conn=pymysql.connect(host='127.0.0.1',port=3306,user='root',password='1234',database='ai_mental_care',charset='utf8mb4')
cur=conn.cursor()
cur.execute("SELECT id, conversation_id, round_num, created_time FROM emotion_diagnosis WHERE conversation_id=%s ORDER BY created_time DESC", (sys.argv[1],))
print(json.dumps([[str(r[0]), str(r[1]), r[2], str(r[3])] for r in cur.fetchall()]))`, cid], { encoding: "utf8", env: { ...process.env, PYTHONIOENCODING: "utf-8" } }).trim();
    const rows = JSON.parse(out);
    report.db = { rowCount: rows.length, latestRow: rows[0] ?? null, latestIdMatchesApi: (rows[0]?.[0] ?? "") === diagnosisId };
  } catch (e) {
    report.db = { error: String(e).slice(0, 300) };
  }
}

const roundsOk = report.rounds.length >= 10 && report.rounds.filter((r) => r.replyOk).length >= 8;
report.ok = roundsOk && report.diagnosis.total > 0 && (report.db.rowCount ?? 0) > 0;
console.log(JSON.stringify({
  cid, diagnosisId,
  rounds: report.rounds.map((r) => ({ replyOk: r.replyOk, seconds: r.seconds })),
  diagnosisTotal: report.diagnosis.total,
  db: report.db,
  ok: report.ok,
}, null, 2));
process.exitCode = report.ok ? 0 : 1;