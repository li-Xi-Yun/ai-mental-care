/**
 * 管理端 Live2D 模型配置页 E2E（真实浏览器，puppeteer-core + Chrome）
 *
 * 验证：真实登录 admin → 打开 /admin/live2d → 上传 Haru.zip（走 el-upload 弹窗）→
 *       列表渲染白名单 tag → 启用互斥 switch → 编辑备注 → 删除。
 *
 * 前置：后端 :8088 + 前端 :5173 均已启动。
 */
import puppeteer from "puppeteer-core";
import { execFileSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const FRONT = "http://localhost:5173/ai-mental-care";
const CHROME = "C:/Program Files/Google/Chrome/Application/chrome.exe";
const MODEL_ZIP = path.join(__dirname, "assets", "Haru.zip");
const MODEL_NAME = `HaruE2E${Date.now()}`;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function captchaCode(key) {
  const py = `
import redis,sys
v=redis.Redis(host='127.0.0.1',port=6379,password='132466',db=0,decode_responses=False).get('verification-code:'+sys.argv[1])
if v is None: print('__NONE__')
else: print(''.join(chr(b) for b in v[1:-1])+chr(v[-1]&127))
`;
  const out = execFileSync("python", ["-c", py, key], { encoding: "utf8", env: { ...process.env, PYTHONIOENCODING: "utf-8" } }).trim();
  return out === "__NONE__" ? null : out;
}

/** 真实 admin 登录（复用页面接口路径），返回 token/headerName */
async function loginAdmin(page) {
  // 先落到后端同源页面（localhost:5173 经 vite 代理可访问 /api）
  await page.goto(FRONT, { waitUntil: "networkidle2", timeout: 30000 });
  const cap = await page.evaluate(async () => {
    const res = await fetch("/api/common/verification/code", { method: "POST" });
    return (await res.json()).data;
  });
  const code = captchaCode(cap.key);
  const raw = await page.evaluate(async ({ key, code }) => {
    const res = await fetch("/api/admin/account/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ loginAccount: "admin", password: "123456", key, code }),
    });
    return await res.text();
  }, { key: cap.key, code });
  const data = JSON.parse(raw).data;
  if (!data?.token) throw new Error(`admin 登录失败: ${raw.slice(0, 200)}`);
  return { token: data.token, headerName: data.headerName || "admin-token" };
}

async function main() {
  const browser = await puppeteer.launch({
    executablePath: CHROME,
    headless: true,
    args: ["--no-sandbox", "--disable-gpu", "--window-size=1440,900"],
  });
  const page = await browser.newPage();
  await page.setViewport({ width: 1440, height: 900 });
  const errors = [];
  const network = [];
  page.on("pageerror", (e) => errors.push(String(e)));
  page.on("console", (m) => { if (m.type() === "error") errors.push(m.text()); });
  page.on("response", (res) => {
    const u = res.url();
    if (u.includes("/admin/live2d-model")) network.push(`${res.request().method()} ${u.replace(/^.*8088/, "")} -> ${res.status()}`);
  });

  const auth = await loginAdmin(page);
  await page.evaluate(({ token, headerName }) => {
    localStorage.setItem("admin_token_info", JSON.stringify({ token, headerName }));
    localStorage.setItem("admin-token", token);
    localStorage.setItem("token", token);
  }, auth);
  console.log("[1] admin 登录成功，注入 token");

  // 打开 Live2D 管理页
  await page.goto(`${FRONT}/admin/live2d`, { waitUntil: "networkidle2", timeout: 30000 });
  await sleep(1500);
  await page.screenshot({ path: path.join(__dirname, "admin-live2d-empty.png") });
  const emptyText = await page.evaluate(() => document.body.innerText);
  if (!emptyText.includes("Live2D模型")) throw new Error("页面标题缺失");
  console.log("[2] /admin/live2d 已打开，页头标题存在");

  // 上传 Haru
  await page.evaluate(() => {
    const btns = [...document.querySelectorAll("button")];
    const up = btns.find((b) => b.innerText.includes("上传模型"));
    up?.click();
  });
  await sleep(800);
  const uploadInput = await page.$(".upload-form input[type=file]");
  if (!uploadInput) throw new Error("上传弹窗文件选择器未找到");
  await uploadInput.uploadFile(MODEL_ZIP);
  await sleep(600);
  // 使用真实键盘输入触发 Vue v-model（仅改 DOM value 不能保证 reactive form 同步）
  const nameInput = await page.$('.upload-form input[placeholder="如 hiyori"]');
  if (!nameInput) throw new Error("未找到模型名称输入框");
  await nameInput.click();
  await nameInput.evaluate((el) => { el.value = ""; });
  await nameInput.type(MODEL_NAME);
  await sleep(400);
  const uploadDebug = await page.evaluate(() => ({
    nameValues: [...document.querySelectorAll(".upload-form input")].map((i) => ({ placeholder: i.getAttribute("placeholder"), value: i.value, type: i.type })),
    uploadText: document.querySelector(".upload-form")?.innerText?.slice(0, 500),
    files: [...document.querySelectorAll(".upload-form .el-upload-list__item")].map((e) => e.innerText),
  }));
  console.log("[debug] upload dialog:", JSON.stringify(uploadDebug));
  await page.screenshot({ path: path.join(__dirname, "admin-live2d-dialog.png") });
  // 点击上传
  const submitDebug = await page.evaluate(() =>
    [...document.querySelectorAll(".el-dialog__footer button")].map((b) => ({
      text: b.innerText,
      disabled: b.disabled,
      html: b.outerHTML.slice(0, 240),
    })),
  );
  console.log("[debug] submit buttons:", JSON.stringify(submitDebug));
  await page.click(".el-dialog__footer .el-button--primary");
  await sleep(8000);
  const uploadState = await page.evaluate(() => ({
    dialogs: [...document.querySelectorAll(".el-dialog")].filter((e) => getComputedStyle(e).display !== "none").map((e) => e.innerText.slice(0, 120)),
    rows: [...document.querySelectorAll(".live2d-table tbody tr")].map((e) => e.innerText.slice(0, 180)),
    messages: [...document.querySelectorAll(".el-message")].map((e) => e.innerText),
    formModelInputs: [...document.querySelectorAll(".upload-form input")].map((i) => i.value),
  }));
  console.log("[debug] after upload:", JSON.stringify(uploadState));
  const afterUpload = await page.evaluate(() => document.body.innerText);
  if (!afterUpload.includes("Haru") && !afterUpload.includes("模型上传成功")) {
    throw new Error(`上传未成功: ${afterUpload.slice(0, 300)}\n网络: ${JSON.stringify(network)}\n控制台: ${JSON.stringify(errors)}`);
  }
  console.log("[3] 上传 Haru 成功，列表已刷新");

  // 断言列表白名单 tag 渲染
  await sleep(800);
  const tags = await page.evaluate(() => {
    const all = [...document.querySelectorAll(".asset-tag")].map((e) => e.innerText);
    return { count: all.length, sample: all.slice(0, 12), hasIdle: all.includes("haru_g_idle"), hasF01: all.includes("F01") };
  });
  if (tags.count < 27 || !tags.hasIdle || !tags.hasF01) {
    throw new Error(`白名单 tag 渲染异常 count=${tags.count} ${JSON.stringify(tags.sample)}`);
  }
  console.log(`[4] 白名单 tag 渲染 OK（共 ${tags.count} 个，含 haru_g_idle/F01）`);
  await page.screenshot({ path: path.join(__dirname, "admin-live2d-uploaded.png") });

  // 启用 switch
  const switchState = await page.evaluate(() => ({
    switches: [...document.querySelectorAll(".live2d-table .el-switch")].map((e) => ({ cls: e.className, text: e.outerHTML.slice(0, 400) })),
  }));
  console.log("[debug] switch:", JSON.stringify(switchState));
  await page.$eval(".live2d-table .el-switch__input", (el) => el.click());
  await sleep(1200);
  const afterEnable = await page.evaluate(() => {
    const m = [...document.querySelectorAll(".el-message")].map((e) => e.innerText);
    const rows = [...document.querySelectorAll(".live2d-table tbody tr")].map((e) => e.innerText.slice(0, 80));
    return { m, rows, hasChecked: !!document.querySelector(".live2d-table .el-switch.is-checked") };
  });
  if (!afterEnable.hasChecked) throw new Error(`启用未成功: ${JSON.stringify(afterEnable)}`);
  console.log("[5] 启用 switch 成功");

  // 编辑备注
  await page.evaluate(() => {
    const btns = [...document.querySelectorAll(".op-btn")];
    const edit = btns.find((b) => b.innerText.includes("编辑"));
    if (!edit) throw new Error("编辑按钮未找到");
    edit.click();
  });
  await sleep(800);
  await page.evaluate(() => {
    const inputs = [...document.querySelectorAll(".edit-form input")];
    const remark = inputs.find((i) => i.getAttribute("placeholder") === "选填");
    if (!remark) throw new Error("编辑备注输入框未找到");
    const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value").set;
    setter.call(remark, "E2E 测试备注");
    remark.dispatchEvent(new Event("input", { bubbles: true }));
  });
  await sleep(300);
  await page.evaluate(() => {
    const btns = [...document.querySelectorAll(".edit-form button")];
    const save = btns.find((b) => b.innerText.includes("保 存") || b.innerText.includes("保存"));
    save?.click();
  });
  await sleep(1200);
  const afterEdit = await page.evaluate(() => document.body.innerText);
  if (!afterEdit.includes("模型配置已更新")) throw new Error(`编辑未成功: ${afterEdit.slice(0, 200)}`);
  console.log("[6] 编辑备注成功");
  await page.screenshot({ path: path.join(__dirname, "admin-live2d-edited.png") });

  // 删除
  await page.evaluate(() => {
    const btns = [...document.querySelectorAll(".op-btn")];
    const del = btns.find((b) => b.innerText.includes("删除"));
    del?.click();
  });
  await sleep(600);
  await page.evaluate(() => {
    const conf = [...document.querySelectorAll(".el-message-box__btns button")];
    const ok = conf.find((b) => b.innerText.includes("删除"));
    ok?.click();
  });
  await sleep(1200);
  const afterDel = await page.evaluate(() => document.body.innerText);
  if (!afterDel.includes("模型已删除")) throw new Error(`删除未成功: ${afterDel.slice(0, 200)}`);
  console.log("[7] 删除成功，列表清空");
  await page.screenshot({ path: path.join(__dirname, "admin-live2d-deleted.png") });

  const body = await page.evaluate(() => document.body.innerText);
  const stillHaru = body.includes("Haru");
  if (stillHaru) throw new Error("删除后列表仍含 Haru");
  console.log("[8] 删除后列表不残留 Haru");

  // 网络请求记录：应包含 上传/分页/启用/更新/删除 各 200
  const net200 = network.filter((n) => n.endsWith("-> 200")).length;
  console.log("[9] 后台 Live2D 请求统计:", JSON.stringify(network));
  if (net200 < 5) throw new Error(`Live2D 后台请求 200 数异常: ${net200}`);

  await browser.close();
  console.log("\n===== UI E2E 全部通过 =====");
  process.exit(0);
}

main().catch((e) => {
  console.error("❌ E2E 失败:", e.message);
  process.exit(1);
});