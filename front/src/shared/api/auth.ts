/**
 * Token / 账号分离存储
 *
 * 前台与后台使用两个独立的 JWT 体系（user-token / admin-token，prod 下均为 Authorization）。
 * 登录接口返回值包含 { token, headerName }：
 *  - headerName：发起带认证请求时应携带的请求头名称（如 user-token / admin-token / Authorization）
 *  - token：该模块的凭证
 *
 * 这里把两套（token + headerName）分开存到 localStorage，避免互相覆盖，
 * 并在发送请求时能根据「请求属于前台接口还是后台接口」取到对应模块的凭证。
 */

/* ==================== 前台「需要登录」弹窗桥接 ====================
 * shared 层不依赖 portal store。前端布局（default.vue）初始化时通过
 * setPromptLoginHandler 注册一个回调（实际是 userStore.openLoginDialog），
 * 401 拦截器在会话过期/游客触发需登录接口时调用 promptUserLogin()，
 * 在当前页面内直接弹出登录弹窗，避免 window.location 硬跳转到不存在的 /login 页面。
 */

/** 全局“需要登录”回调（portal 布局注册；admin 不注册） */
let promptLoginHandler: (() => void) | null = null;

/** 注册/注销「需要登录」时的弹窗回调（由 portal 布局调用） */
export function setPromptLoginHandler(fn: (() => void) | null) {
  promptLoginHandler = fn;
}

/** 触发前台登录弹窗（供 401 拦截器在需要登录时调用） */
export function promptUserLogin() {
  promptLoginHandler?.();
}

/** 前台用户凭证存储 key */
export const STORAGE_KEY_USER_TOKEN = "user_token_info";
/** 后台管理员凭证存储 key */
export const STORAGE_KEY_ADMIN_TOKEN = "admin_token_info";

/** 兼容旧字段（旧代码统一读 localStorage 'token'） */
const LEGACY_TOKEN_KEY = "token";

export interface TokenInfo {
  token: string;
  /** 请求头名称，如 'user-token' / 'admin-token' / 'Authorization' */
  headerName: string;
}

function safeGetKey(key: string): string {
  try {
    return localStorage.getItem(key) || "";
  } catch {
    return "";
  }
}

function safeSetKey(key: string, value: string) {
  try {
    localStorage.setItem(key, value);
  } catch {
    /* ignore quota / privacy errors */
  }
}

function safeRemoveKey(key: string) {
  try {
    localStorage.removeItem(key);
  } catch {
    /* ignore */
  }
}

/** 解析存储的 { token, headerName } */
function parseTokenInfo(raw: string | null): TokenInfo | null {
  if (!raw) return null;
  try {
    const obj = JSON.parse(raw);
    if (obj && typeof obj.token === "string" && obj.token) {
      return { token: obj.token, headerName: obj.headerName || "" };
    }
  } catch {
    /* ignore malformed */
  }
  return null;
}

/** 保存模块 token + headerName */
export function setTokenInfo(kind: "user" | "admin", info: TokenInfo) {
  const key = kind === "admin" ? STORAGE_KEY_ADMIN_TOKEN : STORAGE_KEY_USER_TOKEN;
  safeSetKey(key, JSON.stringify(info));
  // 兼容旧读法（任意一个模块登录时都写 legacy 'token'，便于路由守卫 / 老代码）
  safeSetKey(LEGACY_TOKEN_KEY, info.token);
}

/** 读取模块 token 信息 */
export function getTokenInfo(kind: "user" | "admin"): TokenInfo | null {
  const key = kind === "admin" ? STORAGE_KEY_ADMIN_TOKEN : STORAGE_KEY_USER_TOKEN;
  const parsed = parseTokenInfo(safeGetKey(key));
  if (parsed) return parsed;

  // 兼容：旧代码只存了 'token'，此时回退为默认头名
  const legacy = safeGetKey(LEGACY_TOKEN_KEY);
  if (legacy) return { token: legacy, headerName: "" };
  return null;
}

/** 获取模块 token 字符串 */
export function getToken(kind: "user" | "admin"): string {
  return getTokenInfo(kind)?.token || "";
}

/** 获取模块应携带的请求头名称（空则返回默认头名） */
export function getHeaderName(kind: "user" | "admin", fallback = "Authorization"): string {
  return getTokenInfo(kind)?.headerName || fallback;
}

/** 清除模块 token（含同源的 legacy 键） */
export function clearTokenInfo(kind: "user" | "admin") {
  const key = kind === "admin" ? STORAGE_KEY_ADMIN_TOKEN : STORAGE_KEY_USER_TOKEN;
  const info = parseTokenInfo(safeGetKey(key));
  safeRemoveKey(key);
  // legacy 'token' 键由 setTokenInfo 与模块凭证一并写入，必须同步移除：
  // 否则 getTokenInfo 会回退读到已「退出」的旧凭证，导致退出后请求仍携带旧 token。
  const legacy = safeGetKey(LEGACY_TOKEN_KEY);
  if (legacy && (!info?.token || legacy === info.token)) {
    safeRemoveKey(LEGACY_TOKEN_KEY);
  }
}

/** 清除所有 token */
export function clearAllTokenInfo() {
  safeRemoveKey(STORAGE_KEY_USER_TOKEN);
  safeRemoveKey(STORAGE_KEY_ADMIN_TOKEN);
  safeRemoveKey(LEGACY_TOKEN_KEY);
}

/**
 * 生成带鉴权的请求头（用于 fetch / 下载链接等 axios 之外的场景）。
 * 后端直接读取请求头原始值作为 JWT，不带 "Bearer " 前缀。
 */
export function getAuthHeaders(kind: "user" | "admin"): Record<string, string> {
  const info = getTokenInfo(kind);
  if (!info?.token) return {};
  const headerName = info.headerName || (kind === "admin" ? "admin-token" : "user-token");
  return { [headerName]: info.token };
}