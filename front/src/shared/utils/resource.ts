import { API_BASE_URL } from "@shared/api/config";

/**
 * 解析后端返回的静态资源路径为浏览器可访问 URL。
 *
 * 后端上传接口返回的是相对路径（如 `static-resources/images/xxx.png`，不带前导斜杠），
 * 直接用于 <img src> 会按当前页面地址解析到前端站点（dev 下命中 SPA 回退返回 HTML）导致图片不显示；
 * 需要拼接 API 前缀：dev 由 vite 代理转发到后端，生产同源由 /api 网关转发。
 *
 * - 绝对 URL（http/https/协议相对）、data:、blob: 原样返回；
 * - 空值返回空串。
 */
export function resolveResourceUrl(url?: string | null): string {
  const value = (url ?? "").trim();
  if (!value) return "";
  if (/^(https?:)?\/\//i.test(value) || /^(data|blob):/i.test(value)) return value;
  const base = API_BASE_URL.replace(/\/+$/, "");
  return `${base}/${value.replace(/^\/+/, "")}`;
}
