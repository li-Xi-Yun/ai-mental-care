/**
 * 管理端文件预览大文件 E2E（真实浏览器，puppeteer-core + Chrome）
 *
 * 验证 FilePreviewDialog 大文件文本预览优化：
 *   1. 点击大文件（>512KB txt）预览 → 只发 Range 部分请求（206），不发整包
 *   2. 渲染截断提示条「文件较大，仅预览开头…」
 *   3. 主线程不冻结（布局耗时可控、页面可响应）
 *
 * 前置：后端 :8088 + 前端 :5173 均已启动。
 */
import puppeteer from "puppeteer-core";
import { fileURLToPath } from "node:url";
import path from "node:path";

const ADMIN_TOKEN =
  "eyJhbGciOiJIUzUxMiJ9.eyJlbXBJZCI6IjEifQ.DUYEEMoO08SmPiDkNEgSCZVsRC-o0QwBuG-TFVjwiYLl-OgF7GrmoobrgJCDxZxWHYRr3vAcv2gULVs7ZsodAQ";
const FRONT = "http://localhost:5173/ai-mental-care/admin/file";
// 可通过参数指定预览目标文件（默认大文件 50）
const TARGET_FILE = process.argv[2] ?? "vector_interrupt_big.txt";

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function main() {
  const exe = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
  const browser = await puppeteer.launch({
    headless: "new",
    executablePath: exe,
    args: ["--no-sandbox", "--disable-gpu", "--window-size=1440,900"],
  });
  const page = await browser.newPage();
  const consoleErrors = [];
  page.on("console", (m) => {
    if (m.type() === "error") consoleErrors.push(m.text());
  });
  page.on("pageerror", (e) => consoleErrors.push(String(e)));

  // 捕获对静态资源文件的请求/响应，记录是否有 Range（文本/PDF 等走静态资源直读）
  const txtRequests = [];
  page.on("request", (req) => {
    const u = req.url();
    if (u.includes("/static-resources/files/")) {
      txtRequests.push({ type: "request", url: u, rangeHeader: req.headers()["range"] ?? null });
    }
  });
  page.on("response", (res) => {
    const u = res.url();
    if (u.includes("/static-resources/files/")) {
      txtRequests.push({
        type: "response",
        url: u,
        status: res.status(),
        contentRange: res.headers()["content-range"] ?? null,
        contentLength: res.headers()["content-length"] ?? null,
      });
    }
  });

  // 注入 admin 登录态
  await page.goto("http://localhost:5173/ai-mental-care/", { waitUntil: "domcontentloaded" });
  await page.evaluate((tok) => {
    localStorage.setItem("admin_token_info", JSON.stringify({ token: tok, headerName: "admin-token" }));
    localStorage.setItem("token", tok);
    localStorage.setItem("admin-token", tok);
    localStorage.setItem("admin-user", JSON.stringify({ token: tok }));
  }, ADMIN_TOKEN);

  console.log("[1] 已注入 admin 登录态，打开文件管理页…");
  await page.goto(FRONT, { waitUntil: "networkidle0", timeout: 25000 });
  await sleep(2000);

  // 找到目标文件所在行，点击「预览」
  const clicked = await page.evaluate((name) => {
    const rows = Array.from(document.querySelectorAll(".el-table__body-wrapper tbody tr"));
    for (const tr of rows) {
      if ((tr.textContent || "").includes(name)) {
        const btn = Array.from(tr.querySelectorAll("button")).find((b) => (b.textContent || "").trim() === "预览");
        if (btn) {
          btn.click();
          return true;
        }
      }
    }
    return false;
  }, TARGET_FILE);
  console.log(`[2] 找到「${TARGET_FILE}」行并点击预览: ${clicked}`);
  if (!clicked) {
    console.error("❌ 未在表格中找到目标文件行（可能不在第 1 页）");
    await browser.close();
    process.exit(1);
  }

  await sleep(4000); // 等待弹窗 + fetch + 渲染

  const result = await page.evaluate(() => {
    const tipEls = Array.from(document.querySelectorAll(".preview-tip")).map((e) => e.textContent.trim());
    const pre = document.querySelector("pre.preview-text");
    const rows = (pre?.textContent ?? "").split("\n").length;
    const dialogVisible = !!document.querySelector(".el-dialog:not([style*='display: none'])");
    // 主线程响应性探针：连续强制布局 2000 次所需耗时
    const t0 = performance.now();
    let acc = 0;
    for (let i = 0; i < 2000; i++) acc += document.body.offsetHeight;
    const layoutMs = performance.now() - t0;
    return {
      dialogVisible,
      preExists: !!pre,
      preChars: pre ? pre.textContent.length : 0,
      preLines: rows,
      tips: tipEls,
      layout2000Ms: +layoutMs.toFixed(2),
      hasFreezeText: pre ? /文件较大/.test(document.body.innerText) : false,
    };
  });
  console.log("[3] 弹窗渲染结果:", JSON.stringify(result, null, 2));
  console.log("[4] 静态资源 txt 请求记录:", JSON.stringify(txtRequests, null, 2));
  console.log("[5] 控制台错误:", consoleErrors.length ? consoleErrors.slice(0, 5) : "无");

  const rangeOk = txtRequests.some((r) => r.type === "request" && (r.rangeHeader || "").startsWith("bytes=0-"));
  const statusOk = result.dialogVisible && result.preExists;
  const truncatedOk = result.tips.some((t) => t.includes("文件较大"));
  const frozenOk = result.layout2000Ms < 500; // 布局探针耗时远小于卡死级(数秒)

  const passed = rangeOk && statusOk && truncatedOk && frozenOk;
  console.log("\n=== 验证结果 ===");
  console.log(`✅ Range 部分请求:  ${rangeOk ? "通过" : "失败"}`);
  console.log(`✅ 弹窗+文本渲染:  ${statusOk ? "通过" : "失败"}`);
  console.log(`✅ 截断提示条:      ${truncatedOk ? "通过" : "失败"}`);
  console.log(`✅ 主线程流畅:      ${frozenOk ? "通过" : "失败"} (布局探针 ${result.layout2000Ms}ms < 500ms)`);

  await page.screenshot({ path: path.join("scripts", "admin-file-preview-e2e.png"), fullPage: false });
  console.log("📸 已截图 scripts/admin-file-preview-e2e.png");

  await browser.close();
  process.exit(passed ? 0 : 1);
}

main().catch((e) => {
  console.error("❌ 脚本失败:", e.message);
  process.exit(1);
});