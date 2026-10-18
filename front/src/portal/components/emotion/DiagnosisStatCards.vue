<template>
  <div class="es-summary-cards">
    <div class="es-summary-card" v-for="s in statCards" :key="s.label">
      <span class="label">{{ s.label }}</span>
      <span class="value" :style="s.valueColor ? { color: s.valueColor } : undefined">{{ s.value }}</span>
      <span class="sub">{{ s.sub }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import type { EmotionDiagnosisVO } from "@/portal/api/conversation/emotion-diagnosis";

const props = defineProps<{
  diagnosis: EmotionDiagnosisVO | null;
  emotionRiskMeta: { text: string; type: string };
  crisisText: string;
  formatPercent: (v?: number | null, digits?: number) => string;
  signed: (v?: number | null) => string;
}>();

type StatCard = {
  label: string;
  value: string;
  sub: string;
  valueColor?: string;
};

const RISK_VALUE_COLOR: Record<string, string> = {
  danger: "#ff6b6b",
  warning: "#fa8c16",
  success: "#4ecdc4",
};

const statCards = computed<StatCard[]>(() => {
  const d = props.diagnosis;
  return [
    {
      label: "核心情绪",
      value: d?.coreEmotionLabel ?? "--",
      sub: `置信度 ${props.formatPercent(d?.coreEmotionConfAvg)}`,
    },
    {
      label: "风险等级",
      value: props.emotionRiskMeta.text,
      sub: `危机预警 ${props.crisisText}`,
      valueColor: RISK_VALUE_COLOR[props.emotionRiskMeta.type] ?? "#1a1a2e",
    },
    {
      label: "情绪稳定性",
      value: props.formatPercent(d?.emotionStabilityScore),
      sub: `峰值 第${d?.emotionPeakRound ?? "--"}轮`,
    },
    {
      label: "PAD均值",
      value: `P ${props.signed(d?.avgP)}`,
      sub: `A ${props.signed(d?.avgA)} · D ${props.signed(d?.avgD)}`,
    },
    {
      label: "整体状态",
      value: d?.psychologicalState ?? "--",
      sub: `社会功能 ${d?.socialFunctionImpact ?? "--"}`,
    },
  ];
});
</script>