import { createRouter, createWebHistory } from "vue-router";
import NProgress from "nprogress";
import "nprogress/nprogress.css";
import portalRoutes from "./portal";
import adminRoutes from "./admin";
import { getToken } from "@/shared/api/auth";

const router = createRouter({
  history: createWebHistory(),
  routes: [...portalRoutes, ...adminRoutes],
});

NProgress.configure({ showSpinner: false });

router.beforeEach((to, _from, next) => {
  NProgress.start();
  const isAdminPage = to.path.startsWith("/admin");
  const token = isAdminPage ? getToken("admin") : getToken("user");
  if (to.meta.noAuth || token) {
    next();
  } else if (isAdminPage) {
    next({ name: "AdminLogin" });
  } else {
    next();
  }
});

router.afterEach((to) => {
  NProgress.done();
  document.title = to.meta.title ? `${to.meta.title} - AI Mental Care` : "AI Mental Care";
  // 后台路由挂上 admin-mode：使主题 token（含传送到 body 的弹层）仅作用于后台，不影响 Portal
  document.body.classList.toggle("admin-mode", to.path.startsWith("/admin"));
});

export default router;