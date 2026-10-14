import puppeteer from "puppeteer-core";
import { execFileSync } from "node:child_process";

const browser = await puppeteer.launch({
  executablePath: "C:/Program Files/Google/Chrome/Application/chrome.exe",
  headless: true,
  args: ["--no-sandbox"],
});
const page = await browser.newPage();
await page.goto("http://localhost:5173/ai-mental-care/", { waitUntil: "networkidle2" });

const captcha = await page.evaluate(async () => {
  const r = await fetch("/api/common/verification/code", { method: "POST" });
  return (await r.json()).data;
});
const answer = execFileSync("python", ["-c", `import redis,sys
k=sys.argv[1]
r=redis.Redis(host='127.0.0.1',port=6379,password='132466',db=0,decode_responses=False)
v=r.get('verification-code:'+k)
print(''.join(chr(b) for b in v[1:-1])+chr(v[-1]&0x7f))`, captcha.key], { encoding: "utf8" }).trim();
const login = await page.evaluate(async ({ key, code }) => {
  const r = await fetch("/api/user/account/login", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ loginAccount: "portaltest2", password: "Portal123!", key, code }) });
  return await r.json();
}, { key: captcha.key, code: answer });
if (!login?.data?.token) throw new Error("test login failed");
await page.evaluate((data) => {
  localStorage.setItem("token", data.token);
  localStorage.setItem("user_token_info", JSON.stringify({ token: data.token, headerName: data.headerName || "user-token" }));
  localStorage.setItem("portal-user", JSON.stringify({ token: data.token }));
}, login.data);

await page.goto("http://localhost:5173/ai-mental-care/scale/4/answer", { waitUntil: "networkidle2" });
await new Promise((resolve) => setTimeout(resolve, 1200));
const initial = await page.evaluate(() => ({ body: document.body.innerText.slice(0, 1000), buttons: [...document.querySelectorAll("button")].map((b) => b.innerText.trim()).filter(Boolean) }));
const option = await page.$(".option-item");
if (!option) throw new Error("scale option not found: " + JSON.stringify(initial));
await option.click();
await new Promise((resolve) => setTimeout(resolve, 200));
const submit = await page.$("button.primary");
if (!submit) throw new Error("submit button not found");
await submit.click();
await new Promise((resolve) => setTimeout(resolve, 1800));
console.log(JSON.stringify({ initial, finalUrl: page.url(), body: (await page.evaluate(() => document.body.innerText)).slice(0, 900) }));
await browser.close();
