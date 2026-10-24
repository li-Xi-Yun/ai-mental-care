/**
 * 后台 Live2D 模型配置 API 测试（直连 :8088）
 *
 * 覆盖用例 AP-1..AP-18：登录 → 上传 Hiyori/Haru → 名称占用/非法名称/非zip 负向 →
 * 分页/筛选 → 编辑 → 启用互斥 → 用户端 config → sign-url + file 改写 →
 * 签名篡改/路径越权 → 删除 → 软删查重。
 *
 * 前置：后端 :8088 已启动；/tmp/l2d-test/{Hiyori,Haru}.zip 已打包。
 */
import { execFileSync } from "node:child_process";
import { readFile } from "node:fs/promises";
import { createHash, createHmac } from "node:crypto";

const BASE = "http://localhost:8088";
const REDIS = { host: "127.0.0.1", port: 6379, password: "132466" };

/* ==================== 工具 ==================== */

/** 从 Redis 读取验证码（复用既有脚本的 python 解码逻辑） */
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

/** 后端获取验证码 key */
async function getCaptcha() {
  const res = await fetch(`${BASE}/common/verification/code`, { method: "POST" });
  const body = await res.json();
  if (body?.code !== 200) throw new Error(`验证码接口失败: ${JSON.stringify(body)}`);
  const code = captchaCode(body.data.key);
  return { key: body.data.key, code };
}

/** 登录（admin / user 通用），返回 { token, headerName }；验证码偶发读取失败时重试一次 */
async function login(loginAccount, password, path) {
  for (let attempt = 0; attempt < 2; attempt++) {
    const { key, code } = await getCaptcha();
    const res = await fetch(`${BASE}${path}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ loginAccount, password, key, code }),
    });
    const body = await res.json();
    if (body?.code === 200 && body.data?.token) {
      return { token: body.data.token, headerName: body.data.headerName || "admin-token" };
    }
    if (attempt === 0 && body?.code === 1006) continue; // 验证码错误 → 换新 key 重试
    throw new Error(`登录失败(${loginAccount}): ${JSON.stringify(body)}`);
  }
  throw new Error(`登录失败(${loginAccount}): 重试后仍失败`);
}

function authHeaders(auth) {
  return { [auth.headerName]: auth.token, "Content-Type": "application/json" };
}

/** multipart 上传（Node 内置 FormData） */
async function uploadModel(zipPath, modelName, fields = {}, auth) {
  const buf = await readFile(zipPath);
  const fd = new FormData();
  fd.append("file", new Blob([buf], { type: "application/zip" }), `${modelName}.zip`);
  fd.append("modelName", modelName);
  for (const [k, v] of Object.entries(fields)) if (v !== undefined && v !== null) fd.append(k, String(v));
  const res = await fetch(`${BASE}/admin/live2d-model`, {
    method: "POST",
    headers: { [auth.headerName]: auth.token },
    body: fd,
  });
  return { status: res.status, body: await res.json() };
}

async function api(method, path, auth, body, extra = {}) {
  const headers = auth ? authHeaders(auth) : { "Content-Type": "application/json" };
  const opts = { method, headers };
  if (body !== undefined) opts.body = typeof body === "string" ? body : JSON.stringify(body);
  Object.assign(opts, extra);
  const res = await fetch(`${BASE}${path}`, opts);
  const text = await res.text();
  let parsed;
  try { parsed = JSON.parse(text); } catch { parsed = { raw: text.slice(0, 200) }; }
  return { status: res.status, body: parsed };
}

/* ==================== 断言 ==================== */

let PASS = 0, FAIL = 0;
const failures = [];
function assert(name, cond, detail) {
  if (cond) { PASS += 1; console.log(`  ✅ ${name}`); }
  else { FAIL += 1; failures.push({ name, detail }); console.log(`  ❌ ${name} — ${detail}`); }
}

/* ==================== 主流程 ==================== */

const ADMIN = { loginAccount: "admin", password: "123456" };
const USER = { loginAccount: "portaltest2", password: "Portal123!" };

const results = {};

try {
  console.log("== 登录 ==");
  const adminAuth = await login(ADMIN.loginAccount, ADMIN.password, "/admin/account/login");
  let userAuth = null;
  try { userAuth = await login(USER.loginAccount, USER.password, "/user/account/login"); }
  catch (e) { console.warn("  用户登录跳过:", e.message); }

  /* ---- 负向基础校验（不影响数据的先跑） ---- */
  console.log("== AP-4 非法 modelName ==");
  {
    const { body } = await uploadModel("scripts/assets/Hiyori.zip", "非法.name", {}, adminAuth);
    assert("AP-4 非法 modelName 拒绝", body.code === 3205, `code=${body.code}`);
  }

  console.log("== AP-5 非 zip 文件 ==");
  {
    const fd = new FormData();
    fd.append("file", new Blob(["this is not a zip"], { type: "text/plain" }), "fake.txt");
    fd.append("modelName", "FakeModel");
    const res = await fetch(`${BASE}/admin/live2d-model`, { method: "POST", headers: { [adminAuth.headerName]: adminAuth.token }, body: fd });
    const body = await res.json();
    assert("AP-5 非 zip 拒绝", body.code === 3207 || body.code === 3209, `code=${body.code}`);
  }

  /* ---- 正向上传 ---- */
  console.log("== AP-1 上传 Hiyori ==");
  let hiyoriId;
  {
    const { body } = await uploadModel("scripts/assets/Hiyori.zip", "Hiyori", {}, adminAuth);
    assert("AP-1 上传成功(HTTP200)", body.code === 200, JSON.stringify(body).slice(0, 120));
    hiyoriId = body.data;
    assert("AP-1 返回 id", !!hiyoriId, `id=${hiyoriId}`);
  }

  console.log("== AP-2 上传 Haru ==");
  let haruId;
  {
    const { body } = await uploadModel("scripts/assets/Haru.zip", "Haru", {}, adminAuth);
    assert("AP-2 上传成功", body.code === 200, JSON.stringify(body).slice(0, 120));
    haruId = body.data;
  }

  console.log("== AP-3 重复 modelName ==");
  {
    const { body } = await uploadModel("scripts/assets/Hiyori.zip", "Hiyori", {}, adminAuth);
    assert("AP-3 名称占用拒绝", body.code === 3206, `code=${body.code}`);
  }

  console.log("== AP-6/7/8 分页 ==");
  {
    let r = await api("POST", "/admin/live2d-model/page", adminAuth, { pageNum: 1, pageSize: 10 });
    const list = r.body.data?.records ?? [];
    assert("AP-6 分页返回 2 条", list.length === 2, `len=${list.length}`);
    const hiyori = list.find((x) => x.modelName === "Hiyori");
    const haru = list.find((x) => x.modelName === "Haru");
    assert("AP-6 字段完整", !!hiyori?.modelUrl && !!haru?.modelUrl, `hiyoriUrl=${hiyori?.modelUrl}`);
    results.hiyori = hiyori;
    results.haru = haru;
    // 白名单断言（文件名退化预期）
    const hiyMotions = hiyori.motionList ?? [];
    const hiyExpr = hiyori.expressionList ?? [];
    const haruMotions = haru.motionList ?? [];
    const haruExpr = haru.expressionList ?? [];
    assert("AP-6 Hiyori motionList=10", hiyMotions.length === 10 && hiyMotions.includes("Hiyori_m01") && hiyMotions.includes("Hiyori_m10"), `len=${hiyMotions.length} ${JSON.stringify(hiyMotions.slice(0, 3))}`);
    assert("AP-6 Hiyori expressionList 为空(负向)", hiyExpr.length === 0, `len=${hiyExpr.length}`);
    assert("AP-6 Haru motionList 含 haru_g_idle", haruMotions.includes("haru_g_idle"), JSON.stringify(haruMotions.slice(0, 4)));
    assert("AP-6 Haru expressionList 含 F01..F08", haruExpr.length === 8 && haruExpr.includes("F01") && haruExpr.includes("F08"), `len=${haruExpr.length} ${JSON.stringify(haruExpr.slice(0, 4))}`);

    r = await api("POST", "/admin/live2d-model/page", adminAuth, { pageNum: 1, pageSize: 10, enabled: 0 });
    assert("AP-7 enabled=0 筛选 2 条", (r.body.data?.records ?? []).length === 2, `len=${(r.body.data?.records ?? []).length}`);

    r = await api("POST", "/admin/live2d-model/page", adminAuth, { pageNum: 1, pageSize: 10, modelName: "Hiy" });
    const hiyList = r.body.data?.records ?? [];
    assert("AP-8 modelName 模糊仅 Hiyori", hiyList.length === 1 && hiyList[0].modelName === "Hiyori", `len=${hiyList.length}`);
  }

  console.log("== AP-9 修改配置 ==");
  {
    const dto = { id: hiyoriId, remark: "测试备注-updated", semanticPrompt: "F01=平静,F02=关切", sort: 5 };
    const r = await api("PUT", `/admin/live2d-model/${hiyoriId}`, adminAuth, dto);
    assert("AP-9 更新成功", r.body.code === 200, `code=${r.body.code}`);
    const p = await api("POST", "/admin/live2d-model/page", adminAuth, { pageNum: 1, pageSize: 10, modelName: "Hiyori" });
    const row = p.body.data?.records?.[0];
    assert("AP-9 remark 已更新", row?.remark === "测试备注-updated" && row?.sort === 5 && row?.semanticPrompt === "F01=平静,F02=关切", `remark=${row?.remark} sort=${row?.sort}`);
  }

  console.log("== AP-10 启用互斥 ==");
  {
    let r = await api("PUT", `/admin/live2d-model/${hiyoriId}/enable?enabled=1`, adminAuth);
    assert("AP-10 启用 Hiyori", r.body.code === 200, `code=${r.body.code}`);
    let p = await api("POST", "/admin/live2d-model/page", adminAuth, { pageNum: 1, pageSize: 10 });
    let rows = p.body.data?.records ?? [];
    assert("AP-10 仅 Hiyori enabled=1", rows.find((x) => x.id === hiyoriId)?.enabled === 1 && rows.find((x) => x.id === haruId)?.enabled === 0, `hiy=${rows.find((x) => x.id === hiyoriId)?.enabled} haru=${rows.find((x) => x.id === haruId)?.enabled}`);

    r = await api("PUT", `/admin/live2d-model/${haruId}/enable?enabled=1`, adminAuth);
    assert("AP-10 启用 Haru", r.body.code === 200, `code=${r.body.code}`);
    p = await api("POST", "/admin/live2d-model/page", adminAuth, { pageNum: 1, pageSize: 10 });
    rows = p.body.data?.records ?? [];
    assert("AP-10 互斥：仅 Haru enabled=1", rows.find((x) => x.id === haruId)?.enabled === 1 && rows.find((x) => x.id === hiyoriId)?.enabled === 0, `haru=${rows.find((x) => x.id === haruId)?.enabled} hiy=${rows.find((x) => x.id === hiyoriId)?.enabled}`);
  }

  /* ---- 用户端（user 登录） ---- */
  console.log("== AP-13 用户端 config ==");
  let enabledModelUrl = null;
  if (userAuth) {
    const r = await api("GET", "/user/live2d-model/config", userAuth);
    assert("AP-13 config 返回启用模型(Haru)", r.body.code === 200 && r.body.data?.modelName === "Haru", `modelName=${r.body.data?.modelName}`);
    enabledModelUrl = r.body.data?.modelUrl;
    assert("AP-13 config 带白名单", Array.isArray(r.body.data?.expressionList) && Array.isArray(r.body.data?.motionList), "白名单数组");

    console.log("== AP-14 sign-url + file 改写 ==");
    const s = await api("GET", `/user/live2d-model/sign-url?path=${encodeURIComponent(enabledModelUrl)}`, userAuth);
    const signedPath = s.body.data;
    assert("AP-14 sign-url 返回签名 URL", s.body.code === 200 && typeof signedPath === "string" && signedPath.startsWith("/user/live2d-model/file?"), `url=${signedPath}`);

    const fileRes = await fetch(`${BASE}${signedPath}`);
    const text = await fileRes.text();
    assert("AP-14 file 返回 model3 JSON", fileRes.status === 200 && text.includes('"FileReferences"'), `status=${fileRes.status}`);
    const m3 = JSON.parse(text);
    const fr = m3.FileReferences;
    const mocSigned = typeof fr.Moc === "string" && fr.Moc.startsWith("/user/live2d-model/file?");
    const texSigned = Array.isArray(fr.Textures) && fr.Textures.every((t) => typeof t === "string" && t.startsWith("/user/live2d-model/file?"));
    const motionSigned = Object.values(fr.Motions ?? {}).every((arr) => arr.every((m) => typeof m.File === "string" && m.File.startsWith("/user/live2d-model/file?")));
    assert("AP-14 Moc/Textures/Motions 全部改写为签名 URL", mocSigned && texSigned && motionSigned, `Moc=${fr.Moc?.slice(0, 40)}`);

    console.log("== AP-15 签名篡改（改 expires → 签名不匹配） ==");
    const url = new URL(`${BASE}${signedPath}`);
    const past = String(Math.floor(Date.now() / 1000) - 60);
    const tampered = `${BASE}/user/live2d-model/file?path=${url.searchParams.get("path")}&expires=${past}&sign=${url.searchParams.get("sign")}`;
    const tamperedRes = await fetch(tampered);
    let tamperedBody = {};
    try { tamperedBody = await tamperedRes.json(); } catch { /* 非 JSON */ }
    assert("AP-15 篡改过期时间被拒", tamperedBody.code === 3213 || tamperedRes.status === 200 && tamperedBody.code !== 200, `code=${tamperedBody.code} status=${tamperedRes.status}`);
  } else {
    console.warn("  ⚠ 用户登录不可用，跳过 AP-13/14/15（需 portal 测试账号）");
  }

  console.log("== AP-16 路径越权 ==");
  if (userAuth) {
    const r = await api("GET", "/user/live2d-model/sign-url?path=../../etc/passwd", userAuth);
    assert("AP-16 越权路径被拒", r.body.code === 3212, `code=${r.body.code}`);
  }

  console.log("== AP-17/18 删除与软删查重 ==");
  {
    const r = await api("DELETE", `/admin/live2d-model/${hiyoriId}`, adminAuth);
    assert("AP-17 删除 Hiyori", r.body.code === 200, `code=${r.body.code}`);
    const p = await api("POST", "/admin/live2d-model/page", adminAuth, { pageNum: 1, pageSize: 10 });
    const names = (p.body.data?.records ?? []).map((x) => x.modelName);
    assert("AP-17 分页不再含 Hiyori", !names.includes("Hiyori") && names.includes("Haru"), `names=${names}`);

    const re = await uploadModel("scripts/assets/Hiyori.zip", "Hiyori", {}, adminAuth);
    assert("AP-18 软删后同名仍占用", re.body.code === 3206, `code=${re.body.code}`);
  }

  /* ---- 清理：删除 Haru 恢复初始态 ---- */
  console.log("== 清理 ==");
  await api("DELETE", `/admin/live2d-model/${haruId}`, adminAuth);
  console.log("  已删除 Haru（恢复初始态）");

} catch (e) {
  FAIL += 1;
  failures.push({ name: "脚本异常", detail: e.message });
  console.error("脚本异常:", e);
}

console.log("\n========== 汇总 ==========");
console.log(`PASS=${PASS}  FAIL=${FAIL}`);
if (failures.length) {
  console.log("失败明细:");
  for (const f of failures) console.log(`  - ${f.name}: ${f.detail}`);
}
process.exit(FAIL ? 1 : 0);