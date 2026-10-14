import { defineConfig, loadEnv } from "vite";
import vue from "@vitejs/plugin-vue";
import AutoImport from "unplugin-auto-import/vite";
import Components from "unplugin-vue-components/vite";
import { ElementPlusResolver } from "unplugin-vue-components/resolvers";
import { resolve } from "path";

// 页面统一前缀单源：来自 .env 的 VITE_APP_BASE（.env.development / .env.production 存在同名变量时覆盖 .env）。
// base（构建期资产前缀）与 src/shared/api/config.ts 的 PAGE_BASE_URL（运行期路由 base）必须一致，二者同源于此。
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd());
  return {
    base: env.VITE_APP_BASE || "/",
    plugins: [
      vue(),
      AutoImport({
        resolvers: [ElementPlusResolver()],
        imports: ["vue", "vue-router", "pinia"],
        dts: "src/shared/types/auto-imports.d.ts",
      }),
      Components({
        resolvers: [ElementPlusResolver()],
        dts: "src/shared/types/components.d.ts",
      }),
    ],
  resolve: {
    alias: {
      "@": resolve(__dirname, "src"),
      "@shared": resolve(__dirname, "src/shared"),
      "@portal": resolve(__dirname, "src/portal"),
      "@admin": resolve(__dirname, "src/admin"),
    },
  },
  server: {
    port: 5173,
    proxy: {
      // 子路径前缀：VITE_APP_BASE 非空（如 /ai-mental-care）时，dev 下 API 前缀变成 {pageBase}/api，
      // 需要把 {pageBase} 剥掉再转发，否则后端会收到多余的子路径前缀（404）。
      ...(env.VITE_APP_BASE
        ? {
            [`${env.VITE_APP_BASE}/api`]: {
              target: "http://localhost:8088",
              changeOrigin: true,
              rewrite: (path) => path.replace(new RegExp(`^${env.VITE_APP_BASE}/api`), ""),
            },
          }
        : {}),
      "/api": {
        target: "http://localhost:8088",
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ""),
      },
    },
  },
  };
});