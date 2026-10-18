import { createRouter, createWebHistory } from "vue-router";
import portalRoutes from "./portal";
import adminRoutes from "./admin";
import { getToken } from "@/shared/api/auth";
import { PAGE_BASE_URL, ADMIN_PAGE_PREFIX } from "@/shared/api/config";

const router = createRouter({
  history: createWebHistory(PAGE_BASE_URL),
  routes: [...portalRoutes, ...adminRoutes],
});

router.beforeEach((to, _from, next) => {
  const isAdminPage = to.path.startsWith(ADMIN_PAGE_PREFIX);
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
  document.title = to.meta.title ? `${to.meta.title} - AI Mental Care` : "AI Mental Care";
  // 后台路由挂上 admin-mode：使主题 token（含传送到 body 的弹层）仅作用于后台，不影响 Portal
  document.body.classList.toggle("admin-mode", to.path.startsWith(ADMIN_PAGE_PREFIX));
});

export default router;