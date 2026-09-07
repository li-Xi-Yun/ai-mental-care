import type { RouteRecordRaw } from "vue-router";
import PortalLayout from "@/portal/layouts/default.vue";

const portalRoutes: RouteRecordRaw[] = [
  {
    path: "/",
    component: PortalLayout,
    children: [
      {
        path: "",
        name: "PortalHome",
        component: () => import("@/portal/views/conversation/index.vue"),
        meta: { title: "首页" },
      },
      {
        path: "conversation",
        name: "PortalConversation",
        component: () => import("@/portal/views/conversation/index.vue"),
        meta: { title: "AI对话" },
      },
      {
        path: "emotion",
        name: "PortalEmotion",
        component: () => import("@/portal/views/emotion/index.vue"),
        meta: { title: "情绪分析" },
      },
      {
        path: "scale",
        name: "PortalScale",
        component: () => import("@/portal/views/scale/index.vue"),
        meta: { title: "量表测评" },
      },
      {
        path: "profile",
        name: "PortalProfile",
        component: () => import("@/portal/views/profile/index.vue"),
        meta: { title: "个人中心" },
      },
    ],
  },
  {
    path: "/login",
    name: "PortalLogin",
    component: () => import("@/portal/views/login/index.vue"),
    meta: { title: "用户登录", noAuth: true },
  },
];

export default portalRoutes;