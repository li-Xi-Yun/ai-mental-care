import { execFileSync } from "child_process";

const BASE = "http://localhost:8088";

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
async function getCaptcha() {
  const res = await fetch(`${BASE}/common/verification/code`, { method: "POST" });
  const body = await res.json();
  const code = captchaCode(body.data.key);
  return { key: body.data.key, code };
}
async function login(loginAccount, password, path) {
  for (let attempt = 0; attempt < 2; attempt++) {
    const { key, code } = await getCaptcha();
    const res = await fetch(`${BASE}${path}`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ loginAccount, password, key, code }) });
    const body = await res.json();
    if (body?.code === 200 && body.data?.token) return { token: body.data.token, headerName: body.data.headerName || "admin-token" };
    if (attempt === 0 && body?.code === 1006) continue;
    throw new Error(`登录失败: ${JSON.stringify(body)}`);
  }
  throw new Error("登录失败(重试)");
}
function authHeaders(auth) { return { [auth.headerName]: auth.token, "Content-Type": "application/json" }; }
async function api(method, path, auth, body) {
  const res = await fetch(`${BASE}${path}`, { method, headers: authHeaders(auth), ...(body ? { body: JSON.stringify(body) } : {}) });
  return { status: res.status, body: await res.json() };
}

// JS 复刻 parseLabelMap（与 front/src/admin/utils/live2dLabel.ts 同逻辑）
function parseLabelMap(prompt, whitelist) {
  const result = {};
  if (!prompt || !whitelist?.length) return result;
  const labelBeforeEqual = (text, equalIndex) => {
    const before = text.slice(0, equalIndex);
    const separators = ["映射：", "：", "，", "。", "/"];
    let cut = -1;
    for (const sep of separators) {
      const idx = before.lastIndexOf(sep);
      if (idx > cut) cut = idx + sep.length;
    }
    return before.slice(cut).trim().replace(/^[\s,、，。]+/u, "").split("/")[0].trim();
  };
  for (const clause of prompt.split(/[。]+/).map(s => s.trim()).filter(Boolean)) {
    const re = /[=＝]\s*([A-Za-z0-9_]+)/g;
    let m;
    while ((m = re.exec(clause)) !== null) {
      const id = m[1];
      if (!whitelist.includes(id)) continue;
      const label = labelBeforeEqual(clause, m.index);
      if (label) result[id] = label;
    }
  }
  return result;
}

const adminAuth = await login("admin", "123456", "/admin/account/login");
let r = await api("POST", "/admin/live2d-model/page", adminAuth, { pageNum: 1, pageSize: 100 });
const records = r.body.data?.records ?? [];
for (const row of records) {
  const prompt = row.semanticPrompt ?? "";
  const exprKeys = Object.keys(row.expressionList ?? {});
  const motKeys = Object.keys(row.motionList ?? {});
  if (!prompt) { console.log(`[${row.modelName}] (无语义提示词，导入不可用)`); continue; }
  const parsedExpr = parseLabelMap(prompt, exprKeys);
  const parsedMot = parseLabelMap(prompt, motKeys);
  console.log(`[${row.modelName}] expr ${Object.keys(parsedExpr).length}/${exprKeys.length} mot ${Object.keys(parsedMot).length}/${motKeys.length}`);
  if (Object.keys(parsedExpr).length < exprKeys.length) {
    const miss = exprKeys.filter(id => !(id in parsedExpr));
    console.log(`  expr miss: ${miss.join(",")}`);
  }
  if (Object.keys(parsedMot).length < motKeys.length) {
    const miss = motKeys.filter(id => !(id in parsedMot));
    console.log(`  mot miss: ${miss.join(",")}`);
  }
}
