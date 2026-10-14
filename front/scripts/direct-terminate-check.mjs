import puppeteer from "puppeteer-core";
import { execFileSync } from "node:child_process";
const browser = await puppeteer.launch({ executablePath: "C:/Program Files/Google/Chrome/Application/chrome.exe", headless: true, args: ["--no-sandbox"] });
const page = await browser.newPage();
await page.goto("http://localhost:5173/ai-mental-care/", { waitUntil: "networkidle2" });
const cap = await page.evaluate(async () => (await (await fetch("/api/common/verification/code", { method: "POST" })).json()).data);
const code = execFileSync("python", ["-c", `import redis,sys
v=redis.Redis(host='127.0.0.1',port=6379,password='132466',db=0,decode_responses=False).get('verification-code:'+sys.argv[1])
print(''.join(chr(b) for b in v[1:-1])+chr(v[-1]&127))`, cap.key], { encoding: "utf8", env: { ...process.env, PYTHONIOENCODING: "utf-8" } }).trim();
const login = await page.evaluate(async ({ key, code }) => (await (await fetch("http://localhost:8088/user/account/login", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ loginAccount: "portaltest2", password: "Portal123!", key, code }) })).json()).data, { key: cap.key, code });
const result = await page.evaluate(async (token) => { const r = await fetch("http://localhost:8088/user/scale/assessment/4/terminate", { method: "POST", headers: { "user-token": token } }); return { status: r.status, body: await r.json() }; }, login.token);
console.log(JSON.stringify({ status: result.status, code: result.body?.code, msg: result.body?.msg }));
await browser.close();
