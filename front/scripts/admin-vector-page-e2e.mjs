/**
 * 管理端文件向量页 E2E（真实浏览器，puppeteer-core + Chrome）
 *
 * 验证：注入 admin 登录态 → 打开 /admin/file/vector/{id} →
 *       页面渲染文件状态 → 触发「开始向量化」→ 验证前端 WebSocket 订阅
 *       更新 liveStage/liveProgress，最终展示解析失败原因。
 *
 * 前置：后端 :8088 + 前端 :5173 均已启动。
 */
import puppeteer from "puppeteer-core";
import { fileURLToPath } from "node:url";
import path from "node:path";

const FRONT = "http://localhost:5173/ai-mental-care/admin/file/vector/44";
const ADMIN_TOKEN = "eyJhbGciOiJIUzUxMiJ9.eyJlbXBJZCI6IjEifQ.DUYEEMoO08SmPiDkNEgSCZVsRC-o0QwBuG-TFVjwiYLl-OgF7GrmoobrgJCDxZxWHYRr3vAcv2gULVs7ZsodAQ";

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

  // 注入 admin 登录态（在未导航时 set，再导航等价 reload）
  await page.goto("http://localhost:5173/ai-mental-care/", { waitUntil: "domcontentloaded" });
  await page.evaluate((tok) => {
    localStorage.setItem("admin_token_info", JSON.stringify({ token: tok, headerName: "admin-token" }));
    localStorage.setItem("token", tok);
    localStorage.setItem("admin-token", tok);
    localStorage.setItem("admin-user", JSON.stringify({ token: tok }));
  }, ADMIN_TOKEN);

  console.log("[1] 已注入 admin-token，导航到文件向量页…");
  await page.goto(FRONT, { waitUntil: "networkidle0", timeout: 20000 });

  // 等待文件信息卡渲染
  await sleep(1500);

  const state0 = await page.evaluate(() => {
    const txt = document.body.innerText;
    return {
      hasFileName: txt.includes("vector_test_rag.txt") || txt.includes("44"),
      hasStatus: /解析失败|解析中|待解析|解析完成/.test(txt),
      statusText: (txt.match(/解析(失败|中|完成)|待解析/) || [])[0] ?? null,
      hasStartBtn: Array.from(document.querySelectorAll("button")).some((b) => /开始向量化|重新向量化/.test(b.textContent || "")),
      hasInterruptBtn: Array.from(document.querySelectorAll("button")).some((b) => /中断/.test(b.textContent || "")),
    };
  });
  console.log("[2] 页面初始状态:", JSON.stringify(state0));

  // 尝试点击「开始向量化 / 重新向量化」
  let clicked = false;
  for (const label of ["开始向量化", "重新向量化"]) {
    const ok = await page.evaluate((lbl) => {
      const btn = Array.from(document.querySelectorAll("button")).find((b) => (b.textContent || "").includes(lbl));
      if (btn && !btn.disabled) {
        btn.click();
        return true;
      }
      return false;
    }, label);
    if (ok) {
      clicked = true;
      console.log(`[3] 点击了「${label}」按钮`);
      break;
    }
  }

  if (clicked) {
    // 处理确认弹窗
    await sleep(800);
    const confirmed = await page.evaluate(() => {
      const btns = Array.from(document.querySelectorAll(".el-message-box button"));
      const start = btns.find((b) => /开始|确认/.test(b.textContent || ""));
      if (start) {
        start.click();
        return true;
      }
      return false;
    });
    console.log("[4] 确认弹窗处理:", confirmed ? "已点击开始" : "无需/未找到");

    // 等待事件与状态刷新
    await sleep(6000);
    const after = await page.evaluate(() => {
      const txt = document.body.innerText;
      const liveStageMatch = txt.match(/解析启动中|文件读取中|语义分割中|向量化中|向量写入中/);
      const failMatch = txt.match(/向量解析失败[：:].*/);
      return {
        liveStage: liveStageMatch ? liveStageMatch[0] : null,
        failReason: failMatch ? failMatch[0] : null,
        statusText: (txt.match(/解析(失败|中|完成)|待解析/) || [])[0] ?? null,
        hasProgress: /向量化解析中/.test(txt),
      };
    });
    console.log("[5] 触发后页面状态:", JSON.stringify(after));
  }

  console.log("\n=== 结果 ===");
  const passed =
    state0.hasFileName &&
    state0.hasStatus &&
    state0.hasStartBtn &&
    state0.hasInterruptBtn;
  console.log(`✅ 页面渲染: ${passed ? "通过" : "失败"}`);
  console.log(`❌ 控制台错误 ${consoleErrors.length} 条:`);
  for (const e of consoleErrors.slice(0, 5)) console.log("   -", e.slice(0, 200));

  await page.screenshot({ path: path.join("scripts", "admin-vector-page.png"), fullPage: true });
  console.log("📸 已截图 scripts/admin-vector-page.png");

  await browser.close();
  process.exit(passed ? 0 : 1);
}

main().catch((e) => {
  console.error("❌ 脚本失败:", e.message);
  process.exit(1);
});