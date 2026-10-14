import puppeteer from "puppeteer-core";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import path from "node:path";

const BASE = "http://localhost:5173/ai-mental-care";
const chrome = "C:/Program Files/Google/Chrome/Application/chrome.exe";
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const IMG = path.join(__dirname, "assets", "avatar-test.png");

function captchaCode(key) {
  return execFileSync("python", ["-c", `import redis,sys
v=redis.Redis(host='127.0.0.1',port=6379,password='132466',db=0,decode_responses=False).get('verification-code:'+sys.argv[1])
print(''.join(chr(b) for b in v[1:-1])+chr(v[-1]&127))`, key], { encoding: "utf8", env: { ...process.env, PYTHONIOENCODING: "utf-8" } }).trim();
}

const browser = await puppeteer.launch({ executablePath: chrome, headless: true, args: ["--no-sandbox"] });
const page = await browser.newPage();
await page.setViewport({ width: 1440, height: 900 });
const net = [];
let uploadOk = null;
let profileOk = null;
page.on("response", async (res) => {
  const url = res.url();
  if (url.includes("/api/common/upload/image")) {
    net.push(`POST /common/upload/image -> ${res.status()}`);
    try { uploadOk = { status: res.status(), text: (await res.text()).slice(0, 200) }; } catch { /* ignore */ }
  }
  if (url.includes("/api/user/profile/profileUpdate")) {
    net.push(`PUT /user/profile/profileUpdate -> ${res.status()}`);
    try { profileOk = { status: res.status(), text: (await res.text()).slice(0, 300) }; } catch { /* ignore */ }
  }
});
page.on("console", (m) => { if (m.type() === "error") net.push(`console.error: ${m.text().slice(0, 150)}`); });

await page.goto(`${BASE}/`, { waitUntil: "networkidle2" });
const cap = await page.evaluate(async () => (await (await fetch("/api/common/verification/code", { method: "POST" })).json()).data);
const code = captchaCode(cap.key);
const loginRaw = await page.evaluate(async ({ key, code }) => {
  const r = await fetch("/api/user/account/login", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ loginAccount: "portaltest2", password: "Portal123!", key, code }) });
  return await r.text();
}, { key: cap.key, code });
const token = loginRaw.match(/"token"\s*:\s*"([^"]+)"/)?.[1];
if (!token) throw new Error("login failed");
await page.evaluate((t) => {
  localStorage.setItem("token", t);
  localStorage.setItem("user_token_info", JSON.stringify({ token: t, headerName: "user-token" }));
  localStorage.setItem("portal-user", JSON.stringify({ token: t }));
}, token);

// 打开个人中心，上传头像
await page.goto(`${BASE}/profile`, { waitUntil: "networkidle2" });
await sleep(1200);
const beforeAvatar = await page.evaluate(() => document.querySelector(".avatar-img")?.getAttribute("src") || "");
const input = await page.$(".avatar-uploader input[type=file]");
if (!input) throw new Error("avatar file input not found");
await input.uploadFile(IMG);
for (let i = 0; i < 60 && !profileOk; i++) await sleep(250);
await sleep(800);

const afterAvatarSrc = await page.evaluate(() =>
  document.querySelector(".avatar-img img")?.getAttribute("src")
  || document.querySelector(".el-avatar img")?.getAttribute("src")
  || "",
);
const avatarUrl = uploadOk?.text?.match(/"data"\s*:\s*"([^"]+)"/)?.[1] ?? "";
const msg = await page.evaluate(() => [...document.querySelectorAll(".el-message")].map((e) => e.innerText).join(" | "));

// 头像图片资源可访问性
let imgFetch = null;
if (avatarUrl) {
  imgFetch = await page.evaluate(async (u) => {
    const src = u.startsWith("http") ? u : new URL(u, location.origin).toString();
    const r = await fetch(src);
    return { status: r.status, contentType: r.headers.get("content-type"), bytes: (await r.blob()).size };
  }, afterAvatarSrc || avatarUrl);
}

console.log(JSON.stringify({
  beforeAvatar, avatarUrl, afterAvatarSrc, uploadOk, profileOk, msg, imgFetch, net,
}, null, 2));
await browser.close();
