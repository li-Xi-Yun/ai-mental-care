import puppeteer from "puppeteer-core";
import { execFileSync } from "node:child_process";

const browser = await puppeteer.launch({ executablePath: "C:/Program Files/Google/Chrome/Application/chrome.exe", headless: true, args: ["--no-sandbox"] });
const page = await browser.newPage();
await page.goto("http://localhost:5173/ai-mental-care/", { waitUntil: "networkidle2" });
const captcha = await page.evaluate(async () => (await (await fetch("/api/common/verification/code", { method: "POST" })).json()).data);
const code = execFileSync("python", ["-c", `import redis,sys
v=redis.Redis(host='127.0.0.1',port=6379,password='132466',db=0,decode_responses=False).get('verification-code:'+sys.argv[1])
print(''.join(chr(b) for b in v[1:-1])+chr(v[-1]&127))`, captcha.key], { encoding: "utf8" }).trim();
const login = await page.evaluate(async ({ key, code }) => (await (await fetch("/api/user/account/login", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ loginAccount: "portaltest2", password: "Portal123!", key, code }) })).json()).data, { key: captcha.key, code });
await page.evaluate((d) => { localStorage.setItem("token", d.token); localStorage.setItem("user_token_info", JSON.stringify({ token: d.token, headerName: "user-token" })); localStorage.setItem("portal-user", JSON.stringify({ token: d.token })); }, login);
await page.goto("http://localhost:5173/ai-mental-care/scale/4/answer?recordId=4", { waitUntil: "networkidle2" });
await new Promise((resolve) => setTimeout(resolve, 1200));
const before = await page.evaluate(() => document.body.innerText.slice(0, 700));
const button = await page.$("button.el-button--danger");
if (!button) throw new Error("terminate button not found: " + before);
await button.click();
await new Promise((resolve) => setTimeout(resolve, 300));
const confirms = await page.$$(".el-message-box__btns button");
let confirm = null;
for (const b of confirms) if ((await b.evaluate((el) => el.innerText)).includes("结束测评")) confirm = b;
if (!confirm) throw new Error("confirm button not found");
await confirm.click();
await new Promise((resolve) => setTimeout(resolve, 1200));
console.log(JSON.stringify({ before, finalUrl: page.url(), body: (await page.evaluate(() => document.body.innerText)).slice(0, 600) }));
await browser.close();
