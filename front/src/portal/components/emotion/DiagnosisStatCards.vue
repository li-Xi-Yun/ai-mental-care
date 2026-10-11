<template>
  <section class="stat-strip">
    <div class="stat-card" v-for="s in statCards" :key="s.label">
      <div class="stat-label">{{ s.label }}</div>
      <div class="stat-value">{{ s.value }}</div>
      <div class="stat-sub">{{ s.sub }}</div>
    </div>
  </section>
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

const statCards = computed(() => {
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
  ];
});
</script>

<style scoped>
.stat-strip {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}

.stat-card {
  background: #fff;
  border: 1px solid #ececf2;
  border-radius: 14px;
  padding: 16px 18px;
  transition: box-shadow 0.2s, transform 0.2s;
}

.stat-card:hover {
  box-shadow: 0 6px 18px rgba(26, 26, 46, 0.06);
  transform: translateY(-2px);
}

.stat-label {
  font-size: 12px;
  color: #9aa0b5;
}

.stat-value {
  margin-top: 6px;
  font-size: 20px;
  font-weight: 700;
  color: #1a1a2e;
  line-height: 1.2;
}

.stat-sub {
  margin-top: 6px;
  font-size: 12px;
  color: #b0b6c4;
}
</style>