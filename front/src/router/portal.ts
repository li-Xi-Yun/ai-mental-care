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
        component: () => import("@/portal/views/home/index.vue"),
        meta: { title: "首页" },
      },
      {
        path: "conversation",
        name: "PortalConversation",
        component: () => import("@/portal/views/conversation/index.vue"),
        meta: { title: "AI对话" },
      },
      {
        path: "diagnosis",
        name: "PortalDiagnosis",
        component: () => import("@/portal/views/emotion/index.vue"),
        meta: { title: "心理诊断" },
      },
      {
        path: "diagnosis/:sessionId",
        name: "PortalDiagnosisList",
        component: () => import("@/portal/views/emotion/diagnosis-list.vue"),
        meta: { title: "诊断记录" },
      },
      {
        path: "diagnosis/:sessionId/:diagnosisId",
        name: "PortalDiagnosisDetail",
        component: () => import("@/portal/views/emotion/diagnosis-detail.vue"),
        meta: { title: "诊断详情" },
      },
      {
        path: "scale",
        name: "PortalScale",
        component: () => import("@/portal/views/scale/index.vue"),
        meta: { title: "量表测评" },
      },
      {
        path: "scale/:scaleId/answer",
        name: "PortalScaleAnswer",
        component: () => import("@/portal/views/scale/answer.vue"),
        meta: { title: "量表答题" },
      },
      {
        path: "scale/records",
        name: "PortalScaleRecords",
        component: () => import("@/portal/views/scale/records.vue"),
        meta: { title: "测评记录" },
      },
      {
        path: "scale/records/:recordId",
        name: "PortalScaleRecordDetail",
        component: () => import("@/portal/views/scale/record-detail.vue"),
        meta: { title: "测评详情" },
      },
      {
        path: "profile",
        name: "PortalProfile",
        component: () => import("@/portal/views/profile/index.vue"),
        meta: { title: "个人中心" },
      },
    ],
  },
];

export default portalRoutes;