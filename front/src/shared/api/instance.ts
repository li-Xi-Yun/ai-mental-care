import axios from "axios";
import { API_BASE_URL, DEFAULT_TIMEOUT } from "./config";
import { setupRequestInterceptors, setupResponseInterceptors } from "./interceptors";

const httpClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: DEFAULT_TIMEOUT,
  headers: {
    "Content-Type": "application/json",
  },
  // 保留原始响应文本：统一由响应拦截器用 parseJSONWithBigInt 解析，
  // 避免 axios 默认 JSON.parse 把后端 Long（雪花 ID）舍入丢精度（>2^53 的 ID 会损坏）
  transformResponse: [(data) => data],
});

setupRequestInterceptors(httpClient);
setupResponseInterceptors(httpClient);

export default httpClient;