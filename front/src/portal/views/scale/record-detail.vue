<template>
  <div class="record-detail-page">
    <div class="breadcrumb">
      <router-link to="/scale" class="breadcrumb-link">量表测评</router-link>
      <span class="breadcrumb-sep">›</span>
      <router-link to="/scale/records" class="breadcrumb-link">测评记录</router-link>
      <span class="breadcrumb-sep">›</span>
      <span class="breadcrumb-current">记录 #{{ recordId }}</span>
    </div>

    <div class="detail-grid">
      <div class="detail-card">
        <div class="card-title">📋 基本信息</div>
        <div class="card-body">
          <div class="info-row"><span class="info-label">量表名称：</span>SCL-90 症状自评量表</div>
          <div class="info-row"><span class="info-label">完成时间：</span>2026-09-08 10:30</div>
          <div class="info-row"><span class="info-label">用时：</span>12分钟</div>
        </div>
      </div>

      <div class="detail-card">
        <div class="card-title">📊 评分结果</div>
        <div class="card-body">
          <div class="info-row"><span class="info-label">总分：</span><b>156</b></div>
          <div class="info-row"><span class="info-label">总均分：</span>1.73</div>
          <div class="info-row"><span class="info-label">阳性项目数：</span>36</div>
          <div class="info-row"><span class="info-label">阴性项目数：</span>54</div>
          <div class="info-row"><span class="info-label">阳性症状均分：</span>2.42</div>
          <div class="info-row"><span class="info-label">测评等级：</span><el-tag type="warning" size="small">轻度</el-tag></div>
        </div>
      </div>
    </div>

    <div class="detail-card" style="margin-top: 14px">
      <div class="card-title">📈 因子分分析</div>
      <div class="card-body">
        <el-table :data="factorData" size="small" stripe>
          <el-table-column prop="factor" label="因子" width="140" />
          <el-table-column prop="score" label="因子分" width="80" />
          <el-table-column prop="items" label="项目数" width="80" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.score >= 2 ? 'warning' : 'success'" size="small">{{ row.score >= 2 ? '偏高' : '正常' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="description" label="说明" />
        </el-table>
      </div>
    </div>

    <div class="detail-card" style="margin-top: 14px">
      <div class="card-title">💡 AI 建议</div>
      <div class="card-body">
        <p>根据SCL-90测评结果，您的总体心理健康状况处于<b style="color: #fa8c16">轻度</b>水平。其中<b>焦虑</b>和<b>强迫</b>因子得分偏高，建议关注工作压力管理，适当进行放松训练。如症状持续或加重，建议寻求专业心理咨询。</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRoute } from "vue-router";

const route = useRoute();
const recordId = route.params.recordId as string;

const factorData = [
  { factor: "躯体化", score: 1.4, items: 12, description: "身体不适感偏低" },
  { factor: "强迫症状", score: 2.1, items: 10, description: "有轻度强迫倾向" },
  { factor: "人际敏感", score: 1.8, items: 9, description: "人际交往略敏感" },
  { factor: "抑郁", score: 1.6, items: 13, description: "情绪低落程度偏低" },
  { factor: "焦虑", score: 2.3, items: 7, description: "焦虑水平偏高，需关注" },
  { factor: "敌对", score: 1.2, items: 6, description: "敌对情绪低" },
  { factor: "恐怖", score: 1.1, items: 7, description: "恐怖感低" },
  { factor: "偏执", score: 1.3, items: 6, description: "偏执程度低" },
  { factor: "精神病性", score: 1.2, items: 10, description: "精神病性症状低" },
  { factor: "其他", score: 1.5, items: 7, description: "其他项目偏低" },
];
</script>

<style scoped>
.record-detail-page {
  padding: 24px;
  max-width: 900px;
  margin: 0 auto;
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
  background: #fff;
  border-radius: 10px;
  padding: 18px;
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
  margin-bottom: 6px;
}

.info-label {
  color: #999;
}
</style>