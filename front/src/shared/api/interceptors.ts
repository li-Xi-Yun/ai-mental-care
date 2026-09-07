import type { AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from "axios";
import NProgress from "nprogress";
import "nprogress/nprogress.css";
import { SUCCESS_CODE } from "./config";
import type { Result } from "./types";

NProgress.configure({ showSpinner: false });

export function setupRequestInterceptors(instance: AxiosInstance) {
  instance.interceptors.request.use(
    (config: InternalAxiosRequestConfig) => {
      NProgress.start();
      const token = localStorage.getItem("token");
      if (token && config.headers) {
        config.headers.Authorization = `Bearer ${token}`;
      }
      return config;
    },
    (error) => {
      NProgress.done();
      return Promise.reject(error);
    },
  );
}

export function setupResponseInterceptors(instance: AxiosInstance) {
  instance.interceptors.response.use(
    (response: AxiosResponse<Result>) => {
      NProgress.done();
      const { data } = response;
      if (data.code !== SUCCESS_CODE) {
        return Promise.reject(new Error(data.msg || "请求失败"));
      }
      return response;
    },
    (error) => {
      NProgress.done();
      if (error.response?.status === 401) {
        localStorage.removeItem("token");
        window.location.href = "/login";
      }
      return Promise.reject(error);
    },
  );
}