import puppeteer from "puppeteer-core";
import { execFileSync } from "node:child_process";
const browser = await puppeteer.launch({ executablePath: "C:/Program Files/Google/Chrome/Application/chrome.exe", headless: true, args: ["--no-sandbox"] });
const page = await browser.newPage();
await page.goto("http://localhost:5173/ai-mental-care/", { waitUntil: "networkidle2" });
const captcha = await page.evaluate(async () => (await (await fetch("/api/common/verification/code", { method: "POST" })).json()).data);
const code = execFileSync("python", ["-c", `import redis,sys
v=redis.Redis(host='127.0.0.1',port=6379,password='132466',db=0,decode_responses=False).get('verification-code:'+sys.argv[1])
print(''.join(chr(b) for b in v[1:-1])+chr(v[-1]&127))`, captcha.key], { encoding: "utf8", env: { ...process.env, PYTHONIOENCODING: "utf-8" } }).trim();
const login = await page.evaluate(async ({ key, code }) => (await (await fetch("/api/user/account/login", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ loginAccount: "portaltest2", password: "Portal123!", key, code }) })).json()).data, { key: captcha.key, code });
await page.evaluate((d) => { localStorage.setItem("token", d.token); localStorage.setItem("user_token_info", JSON.stringify({ token: d.token, headerName: "user-token" })); localStorage.setItem("portal-user", JSON.stringify({ token: d.token })); }, login);
const requestRaw = async (url, options = {}) => page.evaluate(async ({ url, options }) => { const r = await fetch(url, { ...options, headers: { "Content-Type": "application/json", "user-token": localStorage.getItem("token") } }); return { status: r.status, text: await r.text() }; }, { url, options });
const init = await requestRaw("/api/user/conversation/lifecycle/init", { method: "POST", body: JSON.stringify({ inputTypes: ["TEXT"], outputTypes: ["TEXT"] }) });
const id = init.text.match(/"conversationId"\s*:\s*(\d+)/)?.[1];
if (!id) throw new Error("no conversation id: " + init.text.slice(0, 300));
const rename = await requestRaw(`/api/user/conversation/${id}/name`, { method: "PUT", body: JSON.stringify({ name: "CRUD测试会话" }) });
const deleteResult = await requestRaw(`/api/user/conversation/${id}`, { method: "DELETE" });
console.log(JSON.stringify({ id, initStatus: init.status, renameStatus: rename.status, renameBody: rename.text.slice(0, 100), deleteStatus: deleteResult.status, deleteBody: deleteResult.text.slice(0, 100) }));
await browser.close();
