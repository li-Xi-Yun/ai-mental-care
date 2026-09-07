import { createRouter, createWebHistory } from "vue-router";
import NProgress from "nprogress";
import "nprogress/nprogress.css";
import portalRoutes from "./portal";
import adminRoutes from "./admin";

const router = createRouter({
  history: createWebHistory(),
  routes: [...portalRoutes, ...adminRoutes],
});

NProgress.configure({ showSpinner: false });

router.beforeEach((to, _from, next) => {
  NProgress.start();
  const token = localStorage.getItem("token");
  if (to.meta.noAuth || token) {
    next();
  } else {
    if (to.path.startsWith("/admin")) {
      next({ name: "AdminLogin" });
    } else {
      next({ name: "PortalLogin" });
    }
  }
});

router.afterEach((to) => {
  NProgress.done();
  document.title = to.meta.title ? `${to.meta.title} - AI Mental Care` : "AI Mental Care";
});

export default router;