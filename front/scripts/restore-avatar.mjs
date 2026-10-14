import puppeteer from "puppeteer-core";
import { execFileSync } from "node:child_process";

const BASE = "http://localhost:5173/ai-mental-care";
const chrome = "C:/Program Files/Google/Chrome/Application/chrome.exe";

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
const raw = await page.evaluate(async ({ key, code }) => {
  const r = await fetch("/api/user/account/login", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ loginAccount: "portaltest2", password: "Portal123!", key, code }) });
  return await r.text();
}, { key: cap.key, code });
const token = raw.match(/"token"\s*:\s*"([^"]+)"/)?.[1];
if (!token) throw new Error("login failed");

const result = await page.evaluate(async (token) => {
  const h = { "Content-Type": "application/json", "user-token": token };
  const p = await (await fetch("/api/user/profile/profile", { headers: h })).json();
  const d = p?.data ?? {};
  const payload = {
    username: d.username ?? "",
    loginAccount: d.loginAccount ?? "",
    avatar: "",
    email: d.email ?? "",
    introduction: d.introduction ?? "",
    gender: d.gender ?? 2,
  };
  if (d.mobile) payload.mobile = d.mobile;
  const r = await fetch("/api/user/profile/profileUpdate", { method: "PUT", headers: h, body: JSON.stringify(payload) });
  const upd = await r.json();
  const after = await (await fetch("/api/user/profile/profile", { headers: h })).json();
  return { updateCode: upd?.code, updateMsg: upd?.msg, avatarAfter: after?.data?.avatar ?? "" };
}, token);

console.log(JSON.stringify(result, null, 2));
await browser.close();
