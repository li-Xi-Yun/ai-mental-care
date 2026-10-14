import puppeteer from "puppeteer-core";
import { execFileSync } from "node:child_process";

const BASE = "http://localhost:5173/ai-mental-care";
const chrome = "C:/Program Files/Google/Chrome/Application/chrome.exe";
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const OLD_PWD = "Portal123!";
const NEW_PWD = "Portal456!";

function captchaCode(key) {
  return execFileSync("python", ["-c", `import redis,sys
v=redis.Redis(host='127.0.0.1',port=6379,password='132466',db=0,decode_responses=False).get('verification-code:'+sys.argv[1])
print(''.join(chr(b) for b in v[1:-1])+chr(v[-1]&127))`, key], { encoding: "utf8", env: { ...process.env, PYTHONIOENCODING: "utf-8" } }).trim();
}

const browser = await puppeteer.launch({ executablePath: chrome, headless: true, args: ["--no-sandbox"] });
const page = await browser.newPage();
await page.setViewport({ width: 1440, height: 900 });
const consoleErrors = [];
page.on("console", (m) => { if (m.type() === "error") consoleErrors.push(m.text().slice(0, 160)); });

/** 用真实登录接口验证密码（只取 code/msg，不输出 token） */
async function apiLogin(password) {
  const cap = await page.evaluate(async () => (await (await fetch("/api/common/verification/code", { method: "POST" })).json()).data);
  const code = captchaCode(cap.key);
  return await page.evaluate(async ({ key, code, password }) => {
    const r = await fetch("/api/user/account/login", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ loginAccount: "portaltest2", password, key, code }),
    });
    const j = await r.json();
    return { http: r.status, code: j.code, msg: j.msg };
  }, { key: cap.key, code, password });
}

async function setInput(el, value) {
  await el.evaluate((input, v) => {
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")?.set;
    setter?.call(input, v);
    input.dispatchEvent(new Event("input", { bubbles: true }));
    input.dispatchEvent(new Event("change", { bubbles: true }));
  }, value);
}

async function fillPwdForm(oldP, newP, confirmP) {
  const inputs = await page.$$(".content-card input[type='password']");
  await setInput(inputs[0], oldP);
  await setInput(inputs[1], newP);
  await setInput(inputs[2], confirmP);
}

async function clickSubmitAndReadMessage() {
  await page.click(".content-card .action-btn");
  for (let i = 0; i < 24; i++) {
    await sleep(250);
    const msg = await page.evaluate(() => [...document.querySelectorAll(".el-message")].map((e) => e.innerText.trim()).join(" | "));
    if (msg) return msg;
  }
  return "";
}

const result = {};

// 0) 初始登录（旧密码）
await page.goto(`${BASE}/`, { waitUntil: "networkidle2" });
result.loginOldBefore = await apiLogin(OLD_PWD);
const initCap = await page.evaluate(async () => (await (await fetch("/api/common/verification/code", { method: "POST" })).json()).data);
const initCode = captchaCode(initCap.key);
const initRaw = await page.evaluate(async ({ key, code }) => {
  const r = await fetch("/api/user/account/login", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ loginAccount: "portaltest2", password: "Portal123!", key, code }) });
  return await r.text();
}, { key: initCap.key, code: initCode });
const token = initRaw.match(/"token"\s*:\s*"([^"]+)"/)?.[1];
await page.evaluate((t) => {
  localStorage.setItem("token", t);
  localStorage.setItem("user_token_info", JSON.stringify({ token: t, headerName: "user-token" }));
  localStorage.setItem("portal-user", JSON.stringify({ token: t }));
}, token);

// 打开个人中心 → 修改密码面板（资料加载完成前菜单不渲染，需等待选择器）
async function openPasswordPanel() {
  await page.waitForSelector(".menu-item", { timeout: 20000 });
  const items = await page.$$(".menu-item");
  for (const item of items) {
    const t = await item.evaluate((el) => el.innerText.trim());
    if (t.includes("修改密码")) { await item.click(); break; }
  }
  await page.waitForSelector(".content-card input[type='password']", { timeout: 10000 });
  await sleep(300);
}

await page.goto(`${BASE}/profile`, { waitUntil: "networkidle2" });
await openPasswordPanel();

// 1) 错误旧密码 → 应失败
await fillPwdForm("WrongOld123!", NEW_PWD, NEW_PWD);
result.wrongOldMessage = await clickSubmitAndReadMessage();
await sleep(1200);
result.loginOldAfterWrongAttempt = await apiLogin(OLD_PWD);

// 2) 正确旧密码 → 改密成功
await fillPwdForm(OLD_PWD, NEW_PWD, NEW_PWD);
result.changeMessage = await clickSubmitAndReadMessage();
await sleep(1200);
result.loginOldAfterChange = await apiLogin(OLD_PWD);
result.loginNewAfterChange = await apiLogin(NEW_PWD);

// 3) 改回原密码
await page.goto(`${BASE}/profile`, { waitUntil: "networkidle2" });
await openPasswordPanel();
await fillPwdForm(NEW_PWD, OLD_PWD, OLD_PWD);
result.revertMessage = await clickSubmitAndReadMessage();
await sleep(1200);
result.loginOldAfterRevert = await apiLogin(OLD_PWD);
result.loginNewAfterRevert = await apiLogin(NEW_PWD);

result.consoleErrors = consoleErrors;
console.log(JSON.stringify(result, null, 2));
await browser.close();
