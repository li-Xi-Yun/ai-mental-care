<template>
  <div class="scale-detail-page">
    <div class="breadcrumb">
      <router-link to="/admin/scale" class="breadcrumb-link">量表管理</router-link>
      <span class="breadcrumb-sep">›</span>
      <span class="breadcrumb-current">{{ scaleName }}</span>
    </div>

    <div class="detail-grid">
      <div class="detail-card">
        <div class="card-title">📋 基本信息</div>
        <div class="card-body">
          <div class="info-row"><span class="info-label">量表名称：</span>{{ scaleName }}</div>
          <div class="info-row"><span class="info-label">题目数量：</span>90</div>
          <div class="info-row"><span class="info-label">评分方式：</span>5级评分（1-5）</div>
          <div class="info-label">量表描述：</div>
          <div class="info-text">SCL-90症状自评量表包含90个项目，涉及感觉、情感、思维、意识、行为直至生活习惯、人际关系、饮食睡眠等方面，采用5级评分制。</div>
        </div>
      </div>

      <div class="detail-card">
        <div class="card-title">📊 因子说明</div>
        <div class="card-body">
          <el-table :data="factors" size="small" stripe>
            <el-table-column prop="name" label="因子名称" />
            <el-table-column prop="items" label="题号范围" />
            <el-table-column prop="count" label="题数" width="60" />
          </el-table>
        </div>
      </div>
    </div>

    <div class="detail-card" style="margin-top: 14px">
      <div class="card-title">📝 题目列表</div>
      <div class="card-body">
        <el-table :data="questions" size="small" stripe>
          <el-table-column prop="index" label="序号" width="60" />
          <el-table-column prop="text" label="题目内容" />
          <el-table-column prop="factor" label="所属因子" width="120" />
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { useRoute } from "vue-router";

const route = useRoute();
const scaleId = route.params.scaleId as string;
const scaleName = ref("SCL-90 症状自评量表");

const factors = [
  { name: "躯体化", items: "1,4,12,27,40,42,48,49,52,53,56,58", count: 12 },
  { name: "强迫症状", items: "3,9,10,28,38,45,46,51,55,65", count: 10 },
  { name: "人际敏感", items: "6,21,34,36,37,41,61,69,73", count: 9 },
  { name: "抑郁", items: "5,14,15,20,22,26,29,30,31,32,54,71,79", count: 13 },
  { name: "焦虑", items: "2,17,23,33,39,57,72,78,80,86", count: 7 },
];

const questions = [
  { index: 1, text: "头痛", factor: "躯体化" },
  { index: 2, text: "神经过敏，心中不踏实", factor: "焦虑" },
  { index: 3, text: "头脑中有不必要的想法或字句盘旋", factor: "强迫症状" },
  { index: 4, text: "头昏或昏倒", factor: "躯体化" },
  { index: 5, text: "对异性的兴趣减退", factor: "抑郁" },
  { index: 6, text: "对旁人责备求全", factor: "人际敏感" },
  { index: 7, text: "感到别人能控制您的思想", factor: "偏执" },
  { index: 8, text: "担心自己的衣饰整齐及仪态的端正", factor: "强迫症状" },
  { index: 9, text: "容易哭泣", factor: "抑郁" },
  { index: 10, text: "感到孤独", factor: "人际敏感" },
];
</script>

<style scoped>
.scale-detail-page {
  background: #fff;
  border-radius: 10px;
  padding: 20px;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #999;
  margin-bottom: 16px;
}

.breadcrumb-link {
  color: #6c63ff;
  text-decoration: none;
}

.breadcrumb-current {
  color: #333;
  font-weight: 500;
}

.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.detail-card {
  border-radius: 8px;
  padding: 16px;
  border: 1px solid #f0f0f0;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: #333;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f0f0;
}

.card-body {
  font-size: 13px;
  line-height: 1.8;
}

.info-row {
  margin-bottom: 4px;
}

.info-label {
  color: #999;
}

.info-text {
  font-size: 12px;
  color: #666;
  line-height: 1.6;
  margin-top: 4px;
}
</style>