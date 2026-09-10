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
        path: "admin",
        name: "AdminAdmin",
        component: () => import("@/admin/views/admin/index.vue"),
        meta: { title: "管理员管理" },
      },
      {
        path: "file",
        name: "AdminFile",
        component: () => import("@/admin/views/file/index.vue"),
        meta: { title: "文件管理" },
      },
      {
        path: "file/vector/:fileId",
        name: "AdminFileVector",
        component: () => import("@/admin/views/file/vector.vue"),
        meta: { title: "文件向量管理" },
      },
      {
        path: "scale",
        name: "AdminScale",
        component: () => import("@/admin/views/scale/index.vue"),
        meta: { title: "量表管理" },
      },
      {
        path: "scale/:scaleId",
        name: "AdminScaleDetail",
        component: () => import("@/admin/views/scale/detail.vue"),
        meta: { title: "量表详情" },
      },
      {
        path: "symptom",
        name: "AdminSymptom",
        component: () => import("@/admin/views/symptom/index.vue"),
        meta: { title: "症状字典" },
      },
      {
        path: "ai-node",
        name: "AdminAiNode",
        component: () => import("@/admin/views/ai-node/index.vue"),
        meta: { title: "AI节点配置" },
      },
      {
        path: "ai-node/:id",
        name: "AdminAiNodeDetail",
        component: () => import("@/admin/views/ai-node/detail.vue"),
        meta: { title: "AI节点详情" },
      },
      {
        path: "skill",
        name: "AdminSkill",
        component: () => import("@/admin/views/skill/index.vue"),
        meta: { title: "Skill管理" },
      },
      {
        path: "profile",
        name: "AdminProfile",
        component: () => import("@/admin/views/profile/index.vue"),
        meta: { title: "个人资料" },
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