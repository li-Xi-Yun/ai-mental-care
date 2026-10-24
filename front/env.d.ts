/// <reference types="vite/client" />

declare module "*.vue" {
  import type { DefineComponent } from "vue";
  const component: DefineComponent<{}, {}, any>;
  export default component;
}

/** Live2D Cubism Core 全局挂载（pixi-live2d-display 运行时要求 window.Live2DCubismCore 已加载） */
interface Window {
  Live2DCubismCore?: unknown;
}