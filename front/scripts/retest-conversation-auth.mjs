import puppeteer from "puppeteer-core";
import { execFileSync } from "node:child_process";

const BASE = "http://localhost:5173/ai-mental-care";
const chrome = "C:/Program Files/Google/Chrome/Application/chrome.exe";
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const CONV_NAME = "自动加载验证会话";

function captchaCode(key) {
  return execFileSync("python", ["-c", `import redis,sys
v=redis.Redis(host='127.0.0.1',port=6379,password='132466',db=0,decode_responses=False).get('verification-code:'+sys.argv[1])
print(''.join(chr(b) for b in v[1:-1])+chr(v[-1]&127))`, key], { encoding: "utf8", env: { ...process.env, PYTHONIOENCODING: "utf-8" } }).trim();
}

async function setInput(page, el, value) {
  await el.evaluate((input, v) => {
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")?.set;
    setter?.call(input, v);
    input.dispatchEvent(new Event("input", { bubbles: true }));
    input.dispatchEvent(new Event("change", { bubbles: true }));
  }, value);
}

const browser = await puppeteer.launch({ executablePath: chrome, headless: true, args: ["--no-sandbox"] });
const context = await browser.createBrowserContext();
const page = await context.newPage();
await page.setViewport({ width: 1440, height: 900 });
const requests = [];
let lastCaptchaKey = null;
page.on("request", (req) => {
  const url = req.url();
  if (url.includes("/api/user/") || url.includes("/api/common/verification")) requests.push(`${req.method()} ${url.replace(/^https?:\/\/[^/]+/, "")}`);
});
page.on("response", async (res) => {
  if (res.url().includes("/common/verification/code")) {
    try {
      const json = await res.json();
      if (json?.data?.key) lastCaptchaKey = json.data.key;
    } catch { /* ignore */ }
  }
});

/* ===== 准备数据：API 登录 → 建一个会话（名称可识别） ===== */
await page.goto(`${BASE}/`, { waitUntil: "networkidle2" });
const prepCap = await page.evaluate(async () => (await (await fetch("/api/common/verification/code", { method: "POST" })).json()).data);
const prepCode = captchaCode(prepCap.key);
const prepLogin = await page.evaluate(async ({ key, code }) => {
  const r = await fetch("/api/user/account/login", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ loginAccount: "portaltest2", password: "Portal123!", key, code }) });
  return (await r.json()).data;
}, { key: prepCap.key, code: prepCode });
const prepToken = prepLogin.token;
// Snowflake ID 必须从原始文本提取：直接 JSON.parse 会因 JS Number 精度丢失末位
const initRaw = await page.evaluate(async (token) => {
  const r = await fetch("/api/user/conversation/lifecycle/init", { method: "POST", headers: { "Content-Type": "application/json", "user-token": token }, body: JSON.stringify({ inputTypes: ["TEXT"], outputTypes: ["TEXT"] }) });
  return await r.text();
}, prepToken);
const conversationId = initRaw.match(/"conversationId"\s*:\s*"?(\d+)/)?.[1] ?? "";
const renameResult = conversationId
  ? await page.evaluate(async ({ token, id, name }) => {
      const r = await fetch(`/api/user/conversation/${id}/name`, { method: "PUT", headers: { "Content-Type": "application/json", "user-token": token }, body: JSON.stringify({ name }) });
      return { status: r.status, body: (await r.text()).slice(0, 120) };
    }, { token: prepToken, id: conversationId, name: CONV_NAME })
  : null;

/* ===== 游客态：清空凭证后重新进入对话页 ===== */
await page.evaluate(() => localStorage.clear());
await page.goto(`${BASE}/conversation`, { waitUntil: "networkidle2" });
await sleep(900);
const guestInitial = { url: page.url(), dialog: await visibleDialog(page), body: (await page.evaluate(() => document.body.innerText)).slice(0, 160) };
await page.type("textarea", "登录触发测试");
await page.click(".send-btn");
await sleep(700);
const guestSend = { dialog: await visibleDialog(page) };

/* ===== 弹窗内真实登录（捕获弹窗自身验证码响应） ===== */
async function fillLoginForm(account, password) {
  const inputs = await page.$$(".el-dialog input");
  await setInput(page, inputs[0], account);
  await setInput(page, inputs[1], password);
  // 点“换一张”让弹窗自己拉一次验证码，随后从捕获到的响应 key 解码
  lastCaptchaKey = null;
  await page.click(".el-dialog .captcha-refresh");
  for (let i = 0; i < 20 && !lastCaptchaKey; i++) await sleep(150);
  if (!lastCaptchaKey) throw new Error("未捕获到弹窗验证码响应");
  const code = captchaCode(lastCaptchaKey);
  await setInput(page, inputs[2], code);
}

async function visibleDialog(p) {
  return p.evaluate(() => [...document.querySelectorAll(".el-dialog")].some((el) => {
    const overlay = el.closest(".el-overlay");
    return !!overlay && getComputedStyle(overlay).display !== "none";
  }));
}

const attemptStates = [];
let loginAttempts = 0;
await fillLoginForm("portaltest2", "Portal123!");
await page.click(".el-dialog button.submit-btn");
await sleep(2500);
loginAttempts++;
attemptStates.push({ attempt: 1, dialogOpen: await visibleDialog(page) });
if (attemptStates[0].dialogOpen) {
  // 失败重试一次（验证码偶发竞态）
  await fillLoginForm("portaltest2", "Portal123!");
  await page.click(".el-dialog button.submit-btn");
  await sleep(2500);
  loginAttempts++;
  attemptStates.push({ attempt: 2, dialogOpen: await visibleDialog(page) });
}

await sleep(1500);
const bodyAfter = await page.evaluate(() => document.body.innerText);
const autoLoad = {
  dialogStillOpen: await visibleDialog(page),
  loginAttempts,
  attemptStates,
  hasConversationName: bodyAfter.includes(CONV_NAME),
  conversationListRequest: requests.some((x) => x.includes("/user/conversation/list")),
  memoryRequest: requests.some((x) => x.includes("/dialogue/") && x.includes("/memory")),
  emotionRequest: requests.some((x) => x.includes("emotion-analysis/list")),
  tokenPersisted: await page.evaluate(() => {
    const info = localStorage.getItem("user_token_info");
    const legacy = localStorage.getItem("token");
    return Boolean(info && JSON.parse(info || "{}")?.token && legacy);
  }),
};

/* ===== 清理：删除本次准备用的会话 ===== */
let cleanup = null;
if (conversationId) {
  cleanup = await page.evaluate(async ({ token, id }) => {
    const r = await fetch(`/api/user/conversation/${id}`, { method: "DELETE", headers: { "user-token": token } });
    return { status: r.status, code: (await r.json())?.code };
  }, { token: prepToken, id: conversationId });
}

console.log(JSON.stringify({ conversationId, renameResult, guestInitial, guestSend, autoLoad, cleanup }, null, 2));
await browser.close();
