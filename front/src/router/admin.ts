import type { RouteRecordRaw } from "vue-router";
import AdminLayout from "@/admin/layouts/default.vue";

const adminRoutes: RouteRecordRaw[] = [
  {
    path: "/admin",
    component: AdminLayout,
    children: [
      {
        path: "",
        name: "AdminHome",
        component: () => import("@/admin/views/user/index.vue"),
        meta: { title: "用户管理" },
      },
      {
        path: "user",
        name: "AdminUser",
        component: () => import("@/admin/views/user/index.vue"),
        meta: { title: "用户管理" },
      },
      {
        path: "file",
        name: "AdminFile",
        component: () => import("@/admin/views/file/index.vue"),
        meta: { title: "文件管理" },
      },
      {
        path: "scale",
        name: "AdminScale",
        component: () => import("@/admin/views/scale/index.vue"),
        meta: { title: "量表管理" },
      },
      {
        path: "symptom",
        name: "AdminSymptom",
        component: () => import("@/admin/views/symptom/index.vue"),
        meta: { title: "症状字典" },
      },
    ],
  },
  {
    path: "/admin/login",
    name: "AdminLogin",
    component: () => import("@/admin/views/login/index.vue"),
    meta: { title: "管理员登录", noAuth: true },
  },
];

export default adminRoutes;