import axios from "axios";
import { API_BASE_URL, DEFAULT_TIMEOUT } from "./config";
import { setupRequestInterceptors, setupResponseInterceptors } from "./interceptors";

const httpClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: DEFAULT_TIMEOUT,
  headers: {
    "Content-Type": "application/json",
  },
});

setupRequestInterceptors(httpClient);
setupResponseInterceptors(httpClient);

export default httpClient;