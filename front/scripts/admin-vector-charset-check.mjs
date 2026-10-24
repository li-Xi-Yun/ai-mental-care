/**
 * 浏览器侧中文乱码最终判定
 *
 * 打开 admin 文件向量页 /admin/file/vector/45（该文件已解析完成且 content 为 UTF-8 中文），
 * 断言：页面正文包含「第一章」「基础知识」「心理测评」，且不含 U+FFFD 替换字符。
 * 前端 axios 按 charset=UTF-8 解析响应 → 浏览器渲染应显示正确中文。
 *
 * 前置：后端 :8088 + 前端 :5173 已启动；admin-token 为 lixiyun 的凭证。
 */
import puppeteer from "puppeteer-core";

const ADMIN_TOKEN = "eyJhbGciOiJIUzUxMiJ9.eyJlbXBJZCI6IjEifQ.DUYEEMoO08SmPiDkNEgSCZVsRC-o0QwBuG-TFVjwiYLl-OgF7GrmoobrgJCDxZxWHYRr3vAcv2gULVs7ZsodAQ";
const PAGE = "http://localhost:5173/ai-mental-care/admin/file/vector/45";
const NEEDLE = ["第一章", "基础知识", "心理测评"];

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function main() {
  const browser = await puppeteer.launch({
    headless: "new",
    executablePath: "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
    args: ["--no-sandbox", "--disable-gpu", "--window-size=1440,900"],
  });
  const page = await browser.newPage();
  const jsErrors = [];
  page.on("pageerror", (e) => jsErrors.push(String(e)));

  await page.goto("http://localhost:5173/ai-mental-care/", { waitUntil: "domcontentloaded" });
  await page.evaluate((tok) => {
    localStorage.setItem("admin_token_info", JSON.stringify({ token: tok, headerName: "admin-token" }));
    localStorage.setItem("token", tok);
    localStorage.setItem("admin-user", JSON.stringify({ token: tok }));
  }, ADMIN_TOKEN);

  await page.goto(PAGE, { waitUntil: "networkidle0", timeout: 20000 });
  // 等向量列表渲染
  await sleep(4000);

  const result = await page.evaluate((needles) => {
    const text = document.body.innerText;
    const has = (s) => text.includes(s);
    const hasReplacement = text.includes("\uFFFD");
    return {
      found: needles.filter((n) => has(n)),
      missing: needles.filter((n) => !has(n)),
      hasReplacement,
      textLen: text.length,
      sample: text.slice(0, 160),
    };
  }, NEEDLE);

  // 输出结果用 JSON 转义，避免终端 GBK 干扰判断
  console.log("[RESULT]", JSON.stringify(result));

  const passed = result.missing.length === 0 && !result.hasReplacement;
  console.log(passed ? "✅ 浏览器渲染中文正常（无 U+FFFD）" : "❌ 浏览器渲染仍异常");

  await page.screenshot({ path: "scripts/admin-vector-charset.png", fullPage: true });
  console.log("📸 截图 scripts/admin-vector-charset.png");
  await browser.close();
  process.exit(passed ? 0 : 1);
}

main().catch((e) => {
  console.error("脚本失败:", e.message);
  process.exit(1);
});
