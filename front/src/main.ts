import { createApp } from "vue";
import { createPinia } from "pinia";
import piniaPluginPersistedstate from "pinia-plugin-persistedstate";
import ElementPlus from "element-plus";
import "element-plus/dist/index.css";
import * as ElementPlusIconsVue from "@element-plus/icons-vue";
import router from "./router";
import App from "./App.vue";
import "@/assets/styles/index.css";
import { setupFrontendLogReporting } from "@/shared/utils/log";

// 前端日志上报（独立基础设施）：在应用启动前注册全局错误/未处理 Promise 拒绝捕获，
// 生产与开发默认开启，localStorage['log-report-enabled'] === '0' 可关闭。
setupFrontendLogReporting();

const app = createApp(App);

const pinia = createPinia();
pinia.use(piniaPluginPersistedstate);

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component);
}

app.use(pinia);
app.use(router);
app.use(ElementPlus);
app.mount("#app");