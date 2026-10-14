import puppeteer from "puppeteer-core";
import { execFileSync } from "node:child_process";

const BASE = "http://localhost:5173/ai-mental-care";
const chrome = "C:/Program Files/Google/Chrome/Application/chrome.exe";
// 早前脚本 Snowflake 精度 bug 导致未删掉的测试会话（portaltest2，name 为空）
const ORPHANS = ["2110255939856289793", "2110256151261794306", "2110045124414853121"];

function captchaCode(key) {
  return execFileSync("python", ["-c", `import redis,sys
v=redis.Redis(host='127.0.0.1',port=6379,password='132466',db=0,decode_responses=False).get('verification-code:'+sys.argv[1])
print(''.join(chr(b) for b in v[1:-1])+chr(v[-1]&127))`, key], { encoding: "utf8", env: { ...process.env, PYTHONIOENCODING: "utf-8" } }).trim();
}

const browser = await puppeteer.launch({ executablePath: chrome, headless: true, args: ["--no-sandbox"] });
const page = await browser.newPage();
await page.goto(`${BASE}/`, { waitUntil: "networkidle2" });
const cap = await page.evaluate(async () => (await (await fetch("/api/common/verification/code", { method: "POST" })).json()).data);
const code = captchaCode(cap.key);
const loginRaw = await page.evaluate(async ({ key, code }) => {
  const r = await fetch("/api/user/account/login", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ loginAccount: "portaltest2", password: "Portal123!", key, code }) });
  return await r.text();
}, { key: cap.key, code });
const token = loginRaw.match(/"token"\s*:\s*"([^"]+)"/)?.[1];
if (!token) throw new Error("login failed: " + loginRaw.slice(0, 200));

const results = [];
for (const id of ORPHANS) {
  const r = await page.evaluate(async ({ token, id }) => {
    const res = await fetch(`/api/user/conversation/${id}`, { method: "DELETE", headers: { "user-token": token } });
    return { status: res.status, body: (await res.text()).slice(0, 100) };
  }, { token, id });
  results.push({ id, ...r });
}
console.log(JSON.stringify(results, null, 2));
await browser.close();
