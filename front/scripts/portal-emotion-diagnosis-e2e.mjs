/**
 * 前台 E2E（变更5）：约 10 轮负面情绪文本对话 → 后端应生成心理评估数据。
 * 同时核验：游客空态（变更1）、情绪面板默认全部收起（变更2）、"查看最新评估"跳转按钮（变更3）。
 *
 * 凭证：PORTAL_TOKEN（优先）或 PORTAL_PWD（验证码登录兜底），仅从环境变量读取，绝不打印。
 * 运行：cd front && PORTAL_TOKEN=xxx node scripts/portal-emotion-diagnosis-e2e.mjs
 * 数据保留（主账号），不清理。
 */
import puppeteer from "puppeteer-core";
import { execFileSync } from "node:child_process";

const BASE = "http://localhost:5173/ai-mental-care";
const chrome = "C:/Program Files/Google/Chrome/Application/chrome.exe";
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const ACCOUNT = process.env.PORTAL_ACCOUNT || "1234567890";
let TOKEN = process.env.PORTAL_TOKEN || "";
const PWD = process.env.PORTAL_PWD || "";

/** 单轮 AI 回复等待上限 */
const REPLY_TIMEOUT_MS = 90_000;
/** 评估异步生成轮询：间隔与总超时 */
const DIAGNOSIS_POLL_MS = 15_000;
const DIAGNOSIS_TIMEOUT_MS = 8 * 60_000;

/** 负面情绪 + 求助信号（覆盖 我是不是/帮我分析/我该怎么办/正常吗/为什么会这样），规避危机词 */
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

const browser = await puppeteer.launch({ executablePath: chrome, headless: true, args: ["--no-sandbox"] });
const page = await browser.newPage();
await page.setViewport({ width: 1440, height: 900 });

const report = { account: ACCOUNT, auth: {}, conversation: {}, rounds: [], diagnosis: {}, db: {}, ui: {}, guest: {}, ok: false };

/** 站内 fetch（同源 → Vite 代理），只返回状态码与原始文本（雪花 ID 由正则提取，禁止 JSON.parse） */
async function api(method, path, body, token) {
  return await page.evaluate(async ({ method, path, body, token }) => {
    const headers = { "Content-Type": "application/json" };
    if (token) headers["user-token"] = token;
    const r = await fetch(`/api${path}`, { method, headers, body: body ? JSON.stringify(body) : undefined });
    return { status: r.status, text: await r.text() };
  }, { method, path, body, token });
}

/* ==================== P0 登录（凭证不回显） ==================== */
await page.goto(`${BASE}/`, { waitUntil: "networkidle2" });

let tokenValid = false;
if (TOKEN) {
  const check = await api("GET", "/user/profile/profile", null, TOKEN);
  tokenValid = check.status === 200 && /"code"\s*:\s*200/.test(check.text);
}
report.auth = { tokenProvided: !!TOKEN, tokenValid, passwordFallback: false };
if (!tokenValid) {
  if (!PWD) throw new Error("登录失败：token 无效且未提供 PORTAL_PWD");
  const capRes = await api("POST", "/common/verification/code");
  const cap = JSON.parse(capRes.text).data;
  const code = captchaCode(cap.key);
  const login = await api("POST", "/user/account/login", { loginAccount: ACCOUNT, password: PWD, key: cap.key, code });
  TOKEN = login.text.match(/"token"\s*:\s*"([^"]+)"/)?.[1] ?? "";
  if (!TOKEN) throw new Error(`登录失败：未能获取 token（http=${login.status}）`);
  report.auth.passwordFallback = true;
  report.auth.loginHttp = login.status;
}
await page.evaluate((t) => {
  localStorage.setItem("token", t);
  localStorage.setItem("user_token_info", JSON.stringify({ token: t, headerName: "user-token" }));
  localStorage.setItem("portal-user", JSON.stringify({ token: t }));
}, TOKEN);

/* ==================== P1 新建会话 ==================== */
const initRes = await api("POST", "/user/conversation/lifecycle/init", { inputTypes: ["TEXT"], outputTypes: ["TEXT"] }, TOKEN);
const conversationId = initRes.text.match(/"conversationId"\s*:\s*"?(\d+)/)?.[1];
if (!conversationId) throw new Error(`会话初始化失败（http=${initRes.status}）`);
report.conversation = { conversationId };

/* ==================== P2 发送约 10 轮负面对话 ==================== */
async function assistantCount() {
  const r = await api("POST", `/user/conversation/dialogue/${conversationId}/memory`, { pageNum: 1, pageSize: 200 }, TOKEN);
  return (r.text.match(/"type"\s*:\s*"assistant"/gi) ?? []).length;
}

async function sendAndWait(text, index) {
  const before = await assistantCount();
  const sent = await api("POST", "/user/conversation/adapter/text/send", { message: text, conversationId }, TOKEN);
  const sendOk = sent.status === 200 && /"code"\s*:\s*200/.test(sent.text);
  const startedAt = Date.now();
  let after = before;
  while (Date.now() - startedAt < REPLY_TIMEOUT_MS) {
    await sleep(3000);
    after = await assistantCount();
    if (after > before) break;
  }
  // 留出 3s 聚合窗口与诊断锁（120s lease）之外的缓冲
  await sleep(2000);
  return { index, sendOk, replyOk: after > before, assistantCount: after, seconds: Math.round((Date.now() - startedAt) / 1000) };
}

for (let i = 0; i < MESSAGES.length; i++) {
  report.rounds.push(await sendAndWait(MESSAGES[i], i + 1));
}

/* ==================== P3 轮询心理评估 ==================== */
async function diagnosisInfo() {
  const r = await api("POST", `/user/conversation/emotion-diagnosis/list/${conversationId}`, { pageNum: 1, pageSize: 1 }, TOKEN);
  const total = Number(r.text.match(/"total"\s*:\s*(\d+)/)?.[1] ?? 0);
  const id = r.text.match(/"records"\s*:\s*\[\s*\{[\s\S]*?"id"\s*:\s*"?(\d+)/)?.[1] ?? "";
  return { total, id, http: r.status };
}

async function pollDiagnosis(deadline) {
  let info = { total: 0, id: "" };
  while (Date.now() < deadline) {
    info = await diagnosisInfo();
    if (info.total > 0) break;
    await sleep(DIAGNOSIS_POLL_MS);
  }
  return info;
}

let diag = await pollDiagnosis(Date.now() + DIAGNOSIS_TIMEOUT_MS);
if (diag.total === 0) {
  // 仍未触发：补 2 轮求助信号消息再轮询
  for (let i = 0; i < EXTRA_MESSAGES.length; i++) {
    report.rounds.push(await sendAndWait(EXTRA_MESSAGES[i], MESSAGES.length + i + 1));
  }
  diag = await pollDiagnosis(Date.now() + 4 * 60_000);
}
report.diagnosis = { total: diag.total, latestId: diag.id, http: diag.http };
const diagnosisId = diag.id;

/* ==================== P4 DB 核验 ==================== */
if (diagnosisId) {
  try {
    const out = execFileSync("python", ["-c", `import pymysql,sys,json
conn=pymysql.connect(host='127.0.0.1',port=3306,user='root',password='1234',database='ai_mental_care',charset='utf8mb4')
cur=conn.cursor()
cur.execute("SELECT id, conversation_id, round_num, created_time FROM emotion_diagnosis WHERE conversation_id=%s ORDER BY created_time DESC", (sys.argv[1],))
print(json.dumps([[str(r[0]), str(r[1]), r[2], str(r[3])] for r in cur.fetchall()]))`, conversationId], { encoding: "utf8", env: { ...process.env, PYTHONIOENCODING: "utf-8" } }).trim();
    const rows = JSON.parse(out);
    report.db = { rowCount: rows.length, latestRow: rows[0] ?? null, latestIdMatchesApi: (rows[0]?.[0] ?? "") === diagnosisId };
  } catch (e) {
    report.db = { error: String(e).slice(0, 300) };
  }
} else {
  report.db = { skipped: "无评估数据" };
}

/* ==================== P5 UI 核验 ==================== */
const ui = {};
if (diagnosisId) {
  // 5.1 选中新会话 + 打开情绪面板 → 默认全部收起 + 跳转按钮存在
  await page.evaluate((s) => localStorage.setItem("portal-conversation", JSON.stringify(s)), {
    currentConversationId: conversationId,
    showConversationList: true,
    showEmotionPanel: true,
    expandedAnalysisId: null,
  });
  await page.goto(`${BASE}/conversation`, { waitUntil: "networkidle2" });
  await page.waitForSelector(".ep-body", { timeout: 20000 }).catch(() => {});
  await page.waitForSelector(".ep-jump", { timeout: 15000 }).catch(() => {});
  await page.waitForSelector(".emo-item", { timeout: 15000 }).catch(() => {});
  await sleep(1000);

  ui.collapsed = await page.evaluate(() => ({
    emoItems: document.querySelectorAll(".emo-item").length,
    expandedItems: document.querySelectorAll(".emo-item.expanded").length,
  }));
  ui.jump = await page.evaluate(() => {
    const el = document.querySelector(".ep-jump");
    return el ? { exists: true, text: el.innerText.replace(/\s+/g, " ").trim() } : { exists: false, text: "" };
  });

  // 5.2 点击跳转 → 最新评估详情页渲染真实数据
  let detailHttp = null;
  page.on("response", (res) => {
    if (res.url().includes("/emotion-diagnosis/detail/")) detailHttp = res.status();
  });
  if (ui.jump.exists) {
    await page.click(".ep-jump");
    let detailUrl = "";
    for (let i = 0; i < 40; i++) {
      await sleep(500);
      detailUrl = page.url();
      if (/\/diagnosis\/\d+\/\d+/.test(detailUrl)) break;
    }
    await page.waitForSelector(".hero-title", { timeout: 20000 }).catch(() => {});
    await sleep(1500);
    ui.detail = await page.evaluate(() => ({
      url: location.pathname,
      heroTitle: document.querySelector(".hero-title")?.textContent?.trim() ?? "",
      coreEmotion: document.querySelector(".core-badge-value")?.textContent?.trim() ?? "",
      hasSummary: !!document.querySelector(".summary-text")?.textContent?.trim(),
    }));
    ui.detail.http = detailHttp;
    ui.detail.urlMatches = new RegExp(`/diagnosis/${conversationId}/${diagnosisId}$`).test(detailUrl.replace(/\/$/, ""));
  }

  // 5.3 反例：无评估的旧会话不显示跳转按钮
  const listRes = await api("POST", "/user/conversation/list", { pageNum: 1, pageSize: 100 }, TOKEN);
  const ids = [...listRes.text.matchAll(/"id"\s*:\s*"?(\d{15,})/g)].map((m) => m[1]);
  const otherId = ids.find((id) => id !== conversationId);
  if (otherId) {
    await page.evaluate((s) => localStorage.setItem("portal-conversation", JSON.stringify(s)), {
      currentConversationId: otherId,
      showConversationList: true,
      showEmotionPanel: true,
      expandedAnalysisId: null,
    });
    await page.goto(`${BASE}/conversation`, { waitUntil: "networkidle2" });
    await page.waitForSelector(".ep-body", { timeout: 20000 }).catch(() => {});
    await sleep(3000);
    ui.noDiagnosisConversation = { id: otherId, jumpAbsent: await page.evaluate(() => !document.querySelector(".ep-jump")) };
  }
}
report.ui = ui;

/* ==================== P6 游客空态（变更1） ==================== */
const guestCtx = await browser.createBrowserContext();
const guestPage = await guestCtx.newPage();
await guestPage.setViewport({ width: 1440, height: 900 });
let guestListRequests = 0;
guestPage.on("request", (req) => {
  if (req.url().includes("/user/conversation/list")) guestListRequests += 1;
});
await guestPage.goto(`${BASE}/conversation`, { waitUntil: "networkidle2" });
await sleep(2000);
const guestView = await guestPage.evaluate(() => ({
  emptyText: document.querySelector(".list-empty")?.innerText?.replace(/\s+/g, " ").trim() ?? "",
  hasSkeleton: !!document.querySelector(".list-skeleton"),
}));
await guestCtx.close();
report.guest = { ...guestView, listRequests: guestListRequests };

/* ==================== 汇总 ==================== */
const roundsOk = report.rounds.length >= 10 && report.rounds.every((r) => r.sendOk && r.replyOk);
report.ok =
  roundsOk &&
  report.diagnosis.total > 0 &&
  (report.db.rowCount ?? 0) > 0 &&
  report.ui.collapsed?.emoItems > 0 &&
  report.ui.collapsed?.expandedItems === 0 &&
  report.ui.jump?.exists === true &&
  report.ui.detail?.heroTitle === "评估详情" &&
  report.ui.detail?.http === 200 &&
  report.ui.noDiagnosisConversation?.jumpAbsent === true &&
  report.guest.emptyText.includes("还没有会话") &&
  report.guest.hasSkeleton === false &&
  report.guest.listRequests === 0;

console.log(JSON.stringify(report, null, 2));
await browser.close();
process.exitCode = report.ok ? 0 : 1;
