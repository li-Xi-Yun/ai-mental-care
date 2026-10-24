/**
 * 管理端文件向量解析「边解析边展示 + 耗时续计」E2E（真实浏览器，puppeteer-core + Chrome）
 *
 * 用中等大小（约 100KB，非大文件）txt 验证三个改造点：
 *   1. 边解析边展示：解析中 stats/paging 接口持续返回已入库分块（L2/分页 total 随批次增长）
 *   2. 耗时不重置：解析中退出重进页面，「已耗时」从服务端 parseStartTime 续计
 *   3. 终态耗时回读：完成后页面展示「解析耗时 XX:XX」，parseEndTime 已写入
 *
 * 流程：上传 → 设置 knowledgeType → 打开向量页 → 开始向量化 → 解析早期先做重进验证 →
 *       持续采样增长 → 等待终态 → 校验终态耗时与 L2 卡 → 清理测试文件。
 * 前置：后端 :8088 + 前端 :5173 均已启动。从 front/ 目录运行。
 */
import puppeteer from "puppeteer-core";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const BASE = "http://localhost:8088";
const ADMIN_TOKEN =
  "eyJhbGciOiJIUzUxMiJ9.eyJlbXBJZCI6IjEifQ.DUYEEMoO08SmPiDkNEgSCZVsRC-o0QwBuG-TFVjwiYLl-OgF7GrmoobrgJCDxZxWHYRr3vAcv2gULVs7ZsodAQ";
const HEADERS = { "admin-token": ADMIN_TOKEN, "Content-Type": "application/json" };

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function api(pathname, options = {}) {
  const res = await fetch(`${BASE}${pathname}`, {
    ...options,
    headers: { ...HEADERS, ...(options.headers || {}) },
  });
  const body = await res.json();
  if (body.code !== 200) throw new Error(`${pathname} -> ${body.code} ${body.msg}`);
  return body.data;
}

/** 生成中等大小中文文本，约 100KB（每个 doc ≈5000-9000 字符 → 触发 AI 语义分割，但总调用次数可控） */
function buildTestFile(sizeBytes = 100 * 1024) {
  const paragraph = [
    "心理健康是整体健康的重要组成部分，良好的情绪管理能力能够帮助个体更好地应对日常压力、维持积极的生活态度。",
    "焦虑与抑郁是当代人群中最常见的心理困扰，早期识别与科学干预对于预后至关重要，必要时应当寻求专业心理支持。",
    "规律作息、适度运动与均衡饮食共同构成健康生活的基础，睡前减少电子屏幕使用有助于改善睡眠质量。",
    "良好的社会支持网络能够显著缓冲压力事件对心理健康的负面影响，保持与家人朋友的有效沟通非常关键。",
    "正念冥想被多项研究证实能够降低焦虑水平、提升专注力，每天十分钟的练习即可带来可观测的改善。",
    "心理咨询并非仅仅针对心理疾病人群，任何希望提升自我认知、改善人际关系的人都可以从中获益。",
  ];
  let out = "";
  let lineNo = 0;
  while (Buffer.byteLength(out, "utf8") < sizeBytes) {
    out += `${lineNo} ` + paragraph[lineNo % paragraph.length] + "\n";
    lineNo++;
  }
  return out;
}

const consoleErrors = [];
const results = {};

async function main() {
  // ---------- 0. 上传 + 设置知识类型 ----------
  const fileContent = buildTestFile();
  const fd = new FormData();
  fd.append("file", new Blob([fileContent], { type: "text/plain" }), `incremental_probe_${Date.now()}.txt`);
  const upRes = await fetch(`${BASE}/admin/file/upload`, {
    method: "POST",
    headers: { "admin-token": ADMIN_TOKEN },
    body: fd,
  });
  const upBody = await upRes.json();
  if (upBody.code !== 200) throw new Error(`upload -> ${upBody.code} ${upBody.msg}`);
  const fileId = upBody.data.id;
  console.log(`[0] 上传测试文件成功 fileId=${fileId} size≈${Math.round(Buffer.byteLength(fileContent) / 1024)}KB`);

  await api(`/admin/file/${fileId}`, {
    method: "PUT",
    body: JSON.stringify({ id: fileId, knowledgeType: 1 }),
  });
  console.log("[1] 已设置 knowledgeType=1");

  // ---------- 1. 打开向量页 ----------
  const exe = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
  const browser = await puppeteer.launch({
    headless: "new",
    executablePath: exe,
    args: ["--no-sandbox", "--disable-gpu", "--window-size=1440,900"],
  });
  const page = await browser.newPage();
  page.on("console", (m) => m.type() === "error" && consoleErrors.push(m.text()));
  page.on("pageerror", (e) => consoleErrors.push(String(e)));

  const FRONT_PAGE = `http://localhost:5173/ai-mental-care/admin/file/vector/${fileId}`;
  await page.goto("http://localhost:5173/ai-mental-care/", { waitUntil: "domcontentloaded" });
  await page.evaluate((tok) => {
    localStorage.setItem("admin_token_info", JSON.stringify({ token: tok, headerName: "admin-token" }));
    localStorage.setItem("token", tok);
    localStorage.setItem("admin-token", tok);
    localStorage.setItem("admin-user", JSON.stringify({ token: tok }));
  }, ADMIN_TOKEN);

  await page.goto(FRONT_PAGE, { waitUntil: "networkidle0", timeout: 25000 });
  await sleep(1500);
  const initText = await page.evaluate(() => document.body.innerText);
  console.log(
    `[2] 初始: ${initText.includes("待解析") ? "待解析 ✓" : "状态异常"} / L2卡=${initText.includes("二级分块 L2") ? "存在 ✓" : "缺失"}`,
  );

  // ---------- 2. 触发向量化 ----------
  const clicked = await page.evaluate(() => {
    const btn = Array.from(document.querySelectorAll("button")).find((b) =>
      /开始向量化|重新向量化/.test(b.textContent || ""),
    );
    if (btn && !btn.disabled) {
      btn.click();
      return true;
    }
    return false;
  });
  if (!clicked) throw new Error("未找到「开始向量化」按钮");
  await sleep(800);
  await page.evaluate(() => {
    const btn = Array.from(document.querySelectorAll(".el-message-box button")).find((b) =>
      /开始|确认/.test(b.textContent || ""),
    );
    if (btn) btn.click();
  });
  console.log("[3] 已点击开始向量化");
  await sleep(3000);

  const startInfo = await api(`/admin/file/${fileId}`);
  console.log(`[4] 解析中: status=${startInfo.status} parseStartTime=${startInfo.parseStartTime} parseEndTime=${startInfo.parseEndTime}`);

  // ---------- 3. 解析早期做「重进页面耗时续计」验证 ----------
  const toSec = (s) => {
    const p = s.split(":").map(Number);
    return p.length === 3 ? p[0] * 3600 + p[1] * 60 + p[2] : p[0] * 60 + p[1];
  };
  const infoEarly = await api(`/admin/file/${fileId}`);
  if (infoEarly.status === 1) {
    const text1 = await page.evaluate(() => document.body.innerText);
    const m1 = (text1.match(/已耗时\s*([\d:]+)/) || [])[1];
    console.log(`[5a] 重进前「已耗时」=${m1 ?? "未找到"}`);

    await page.goto("http://localhost:5173/ai-mental-care/admin/file", { waitUntil: "networkidle0", timeout: 25000 });
    await sleep(1200);
    await page.goto(FRONT_PAGE, { waitUntil: "networkidle0", timeout: 25000 });
    await sleep(1500);
    const text2 = await page.evaluate(() => document.body.innerText);
    const m2 = (text2.match(/已耗时\s*([\d:]+)/) || [])[1];
    console.log(`[5b] 重进后「已耗时」=${m2 ?? "未找到"}`);
    if (m1 && m2) {
      const delta = toSec(m2) - toSec(m1);
      console.log(`   相差 ${delta}s（>0 = 从服务端 parseStartTime 续计，未重置）`);
      results.reentry = { before: m1, after: m2, delta };
    }
  } else {
    console.log("[5] 解析已结束，跳过重进验证");
  }

  // ---------- 4. 持续采样增量（解析中，最长 3 分钟） ----------
  const l2History = [];
  const sampleUntil = Date.now() + 200_000;
  let terminal = null;
  while (Date.now() < sampleUntil) {
    const info = await api(`/admin/file/${fileId}`);
    const st = await api(`/admin/file/vector/${fileId}/stats`);
    const pg = await api(`/admin/file/vector/${fileId}?pageNum=1&pageSize=10`);
    const ts = new Date().toLocaleTimeString();
    console.log(`[采样 ${ts}] status=${info.status} L1=${st.level1Count} L2=${st.level2Count} total=${pg.total}`);
    if (info.status === 1) l2History.push(st.level2Count);
    if ([2, 3].includes(info.status)) {
      terminal = info;
      break;
    }
    await sleep(2000);
  }

  const distinctL2 = new Set(l2History).size;
  const grew = l2History.length >= 2 && l2History[l2History.length - 1] > l2History[0];
  console.log(`[6] 增量观测: ${l2History.length} 个采样点 L2序列=[${l2History.join(",")}] → ${grew ? "增长 ✓" : "未增长"}`);

  // ---------- 5. 等待终态（若尚未到达） ----------
  if (!terminal) {
    const termWait = Date.now() + 300_000;
    while (Date.now() < termWait) {
      const info = await api(`/admin/file/${fileId}`);
      if ([2, 3].includes(info.status)) {
        terminal = info;
        break;
      }
      await sleep(3000);
    }
  }

  if (terminal?.status === 3) {
    const st = await api(`/admin/file/vector/${fileId}/stats`);
    const pg = await api(`/admin/file/vector/${fileId}?pageNum=1&pageSize=5`);
    const startMs = new Date(terminal.parseStartTime).valueOf();
    const endMs = new Date(terminal.parseEndTime).valueOf();
    results.durationSec = Number.isFinite(startMs) && Number.isFinite(endMs) ? Math.floor((endMs - startMs) / 1000) : null;
    console.log(
      `[7] 解析完成: status=3 parseStart=${terminal.parseStartTime} parseEnd=${terminal.parseEndTime} L2=${st.level2Count} total=${pg.total} 耗时=${results.durationSec}s`,
    );

    await page.goto(FRONT_PAGE, { waitUntil: "networkidle0", timeout: 25000 });
    await sleep(1500);
    const finalText = await page.evaluate(() => document.body.innerText);
    const cost = (finalText.match(/解析耗时\s*([\d:]+)/) || [])[1];
    const l2Card = finalText.includes("二级分块 L2");
    console.log(`[8] 终态页面: 「解析耗时」=${cost ?? "未找到"} / L2卡=${l2Card ? "存在 ✓" : "缺失"}`);
    results.finalCostOnPage = cost ?? null;
    results.l2Card = l2Card;
    if (cost && results.durationSec !== null) {
      results.pageCostMatches = Math.abs(toSec(cost) - results.durationSec) <= 1;
    }
  } else if (terminal?.status === 2) {
    console.log(`[7] 解析失败: ${terminal.failReason}`);
  } else {
    console.log("[7] 超时未完成，发起中断清理");
    await api(`/admin/file/vector/${fileId}/interrupt`, { method: "POST" }).catch(() => {});
  }

  await page.screenshot({ path: path.join(__dirname, "admin-vector-incremental.png"), fullPage: true });
  console.log("📸 已截图 scripts/admin-vector-incremental.png");

  // ---------- 6. 清理 ----------
  try {
    const c = await api(`/admin/file/${fileId}`);
    if (c.status === 3) await api(`/admin/file/vector/${fileId}`, { method: "DELETE" });
  } catch (e) {
    console.log("清理向量失败（忽略）:", e.message);
  }
  await api(`/admin/file/${fileId}`, { method: "DELETE" });
  console.log(`[9] 测试文件 ${fileId} 已清理`);

  await browser.close();

  // ---------- 判定 ----------
  console.log("\n=== 验证结果 ===");
  console.log(`✅ 边解析边展示（L2 随批次增长）: ${grew ? "通过" : `未观测到增长（采样序列 [${l2History.join(",")}]）`}`);
  console.log(`✅ 重进页面耗时续计: ${results.reentry ? `通过 ${JSON.stringify(results.reentry)}` : "跳过/失败"}`);
  console.log(`✅ 终态耗时回读: ${results.durationSec !== null ? `通过 ${results.durationSec}s（页面${results.pageCostMatches ? "一致 ✓" : "不一致"}）` : "失败"}`);
  console.log(`✅ 终态 L2 卡: ${results.l2Card ? "通过" : "失败"}`);
  console.log(`❌ 控制台错误 ${consoleErrors.length} 条:`, consoleErrors.slice(0, 5));

  const passed = grew && !!results.reentry && results.durationSec !== null && results.l2Card;
  process.exit(passed ? 0 : 1);
}

main().catch((e) => {
  console.error("❌ 脚本失败:", e.message);
  process.exit(1);
});