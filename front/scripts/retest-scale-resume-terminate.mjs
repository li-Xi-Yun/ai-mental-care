import puppeteer from "puppeteer-core";
import { execFileSync } from "node:child_process";

const BASE = "http://localhost:5173/ai-mental-care";
const chrome = "C:/Program Files/Google/Chrome/Application/chrome.exe";
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function captchaCode(key) {
  return execFileSync("python", ["-c", `import redis,sys
v=redis.Redis(host='127.0.0.1',port=6379,password='132466',db=0,decode_responses=False).get('verification-code:'+sys.argv[1])
print(''.join(chr(b) for b in v[1:-1])+chr(v[-1]&127))`, key], { encoding: "utf8", env: { ...process.env, PYTHONIOENCODING: "utf-8" } }).trim();
}

const browser = await puppeteer.launch({ executablePath: chrome, headless: true, args: ["--no-sandbox"] });
const page = await browser.newPage();
const calls = [];
const consoleErrors = [];
page.on("request", (req) => {
  const url = req.url();
  if (url.includes("/api/user/scale/assessment")) {
    calls.push(`${req.method()} ${url.replace(/^https?:\/\/[^/]+/, "")} body=${(req.postData() || "").slice(0, 120)}`);
  }
});
page.on("console", (msg) => { if (msg.type() === "error") consoleErrors.push(msg.text().slice(0, 200)); });

await page.goto(`${BASE}/`, { waitUntil: "networkidle2" });
const cap = await page.evaluate(async () => (await (await fetch("/api/common/verification/code", { method: "POST" })).json()).data);
const code = captchaCode(cap.key);
const login = await page.evaluate(async ({ key, code }) => {
  const r = await fetch("/api/user/account/login", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ loginAccount: "portaltest2", password: "Portal123!", key, code }) });
  return (await r.json()).data;
}, { key: cap.key, code });
await page.evaluate((d) => {
  localStorage.setItem("token", d.token);
  localStorage.setItem("user_token_info", JSON.stringify({ token: d.token, headerName: "user-token" }));
  localStorage.setItem("portal-user", JSON.stringify({ token: d.token }));
}, login);

// 1) 通过真实接口创建一条未完成记录 R1
const startRes = await page.evaluate(async () => {
  const r = await fetch("/api/user/scale/assessment/start", { method: "POST", headers: { "Content-Type": "application/json", "user-token": localStorage.getItem("token") }, body: JSON.stringify({ scaleId: 4 }) });
  return { status: r.status, body: await r.json() };
});
const r1 = startRes.body?.data?.recordId;
calls.length = 0;

// 2) 打开测评记录页，点击未完成记录的“继续作答 →”
await page.goto(`${BASE}/scale/records`, { waitUntil: "networkidle2" });
await sleep(1200);
const cards = await page.$$(".record-card");
let clicked = null;
for (const card of cards) {
  const text = await card.evaluate((el) => el.innerText);
  if (text.includes("继续作答")) { await card.click(); clicked = text.split("\n")[0]; break; }
}
await sleep(2200);
const urlAfterClick = page.url();
const bodyAfterClick = await page.evaluate(() => document.body.innerText);
const answerLoaded = bodyAfterClick.includes("E2E question X7K9");
const resumeCall = calls.find((c) => c.includes("/assessment/resume"));
const startCall = calls.find((c) => c.includes("/assessment/start"));

// 3) 在答题页点击“结束测评，不保存本次作答”，确认终止
let terminateUi = { buttonFound: false, confirmed: false, finalUrl: "" };
if (answerLoaded) {
  const quitBtn = await page.$(".quit-row button");
  if (quitBtn) {
    terminateUi.buttonFound = true;
    await quitBtn.click();
    await sleep(600);
    const confirmBtn = await page.evaluateHandle(() => {
      const btns = [...document.querySelectorAll(".el-message-box__btns button")];
      return btns.find((b) => b.innerText.includes("结束测评")) || null;
    });
    const el = confirmBtn.asElement();
    if (el) {
      terminateUi.confirmed = true;
      await el.click();
      await sleep(1800);
    }
  }
  terminateUi.finalUrl = page.url();
}

console.log(JSON.stringify({
  startStatus: startRes.status, r1,
  clickedCard: clicked,
  urlAfterClick,
  resumeCall: resumeCall || null,
  startCallAfter: startCall || null,
  answerLoaded,
  terminateUi,
  consoleErrors,
}, null, 2));
await browser.close();
