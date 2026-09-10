<template>
  <div class="scale-page">
    <div class="scale-categories">
      <el-radio-group v-model="categoryFilter" size="default">
        <el-radio-button value="all">全部</el-radio-button>
        <el-radio-button value="anxiety">焦虑评估</el-radio-button>
        <el-radio-button value="depression">抑郁评估</el-radio-button>
        <el-radio-button value="stress">压力评估</el-radio-button>
        <el-radio-button value="sleep">睡眠评估</el-radio-button>
      </el-radio-group>
    </div>

    <div class="scale-cards">
      <div
        v-for="scale in filteredScales"
        :key="scale.id"
        class="scale-card"
      >
        <div class="scale-icon" :style="{ background: scale.iconBg }">{{ scale.icon }}</div>
        <div class="scale-name">{{ scale.name }}</div>
        <div class="scale-desc">{{ scale.description }}</div>
        <el-button type="primary" size="small" class="start-btn" @click="handleStartScale(scale)">开始测评</el-button>
      </div>
    </div>

    <div class="scale-records">
      <div class="records-title">我的测评记录</div>
      <el-table :data="scaleRecords" stripe style="width: 100%">
        <el-table-column prop="name" label="量表名称" />
        <el-table-column prop="time" label="测评时间" width="140" />
        <el-table-column prop="score" label="得分" width="80" />
        <el-table-column label="结果" width="120">
          <template #default="{ row }">
            <el-tag :type="row.resultType" size="small">{{ row.result }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default>
            <el-button type="primary" text size="small">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import { useRouter } from "vue-router";
import { SCALE_CATEGORIES } from "@/shared/api/config";

const router = useRouter();

const categoryFilter = ref("all");

const scaleList = ref([
  { id: "1", name: "SAS 焦虑自评量表", description: "评估焦虑状态程度", icon: "😰", iconBg: "#fff0f0", category: "anxiety" },
  { id: "2", name: "SDS 抑郁自评量表", description: "评估抑郁状态程度", icon: "😔", iconBg: "#f0f5ff", category: "depression" },
  { id: "3", name: "PSQI 睡眠质量量表", description: "评估睡眠质量状况", icon: "😴", iconBg: "#f0fff0", category: "sleep" },
  { id: "4", name: "PSS 压力知觉量表", description: "评估心理压力水平", icon: "😤", iconBg: "#fff7e6", category: "stress" },
  { id: "5", name: "GAD-7 广泛性焦虑", description: "快速筛查焦虑症状", icon: "😟", iconBg: "#fff0f0", category: "anxiety" },
  { id: "6", name: "PHQ-9 抑郁症筛查", description: "快速筛查抑郁症状", icon: "😞", iconBg: "#f0f5ff", category: "depression" },
]);

const filteredScales = computed(() => {
  if (categoryFilter.value === "all") return scaleList.value;
  return scaleList.value.filter((s) => s.category === categoryFilter.value);
});

const scaleRecords = ref([
  { name: "SAS 焦虑自评", time: "2026-09-08", score: 52, result: "轻度焦虑", resultType: "warning" as const },
  { name: "SDS 抑郁自评", time: "2026-09-05", score: 38, result: "正常", resultType: "success" as const },
  { name: "PSQI 睡眠质量", time: "2026-09-01", score: 8, result: "轻度障碍", resultType: "warning" as const },
  { name: "GAD-7 焦虑筛查", time: "2026-08-28", score: 5, result: "轻度", resultType: "warning" as const },
]);

function handleStartScale(scale: any) {
  router.push(`/scale/${scale.id}/answer`);
}
</script>

<style scoped>
.scale-page {
  padding: 24px;
  max-width: 900px;
  margin: 0 auto;
}

.scale-categories {
  margin-bottom: 20px;
}

.scale-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.scale-card {
  background: #fff;
  border-radius: 10px;
  padding: 20px;
  border: 1px solid #f0f0f0;
  text-align: center;
  transition: box-shadow 0.2s, transform 0.2s;
}

.scale-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  transform: translateY(-2px);
}

.scale-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  margin: 0 auto 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}

.scale-name {
  font-size: 14px;
  font-weight: 600;
  color: #333;
  margin-bottom: 4px;
}

.scale-desc {
  font-size: 12px;
  color: #999;
  margin-bottom: 12px;
}

.start-btn {
  border-radius: 16px;
}

.scale-records {
  background: #fff;
  border-radius: 10px;
  padding: 20px;
  border: 1px solid #f0f0f0;
}

.records-title {
  font-size: 15px;
  font-weight: 600;
  color: #333;
  margin-bottom: 12px;
}
</style>