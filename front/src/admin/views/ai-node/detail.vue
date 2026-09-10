<template>
  <div class="ai-node-detail-page">
    <div class="breadcrumb">
      <router-link to="/admin/ai-node" class="breadcrumb-link">AI节点配置</router-link>
      <span class="breadcrumb-sep">›</span>
      <span class="breadcrumb-current">{{ nodeName }}</span>
    </div>

    <div class="detail-grid">
      <div class="detail-card">
        <div class="card-title">📋 基本信息</div>
        <div class="card-body">
          <div class="info-row"><span class="info-label">节点名称：</span>{{ nodeName }}</div>
          <div class="info-row"><span class="info-label">分组：</span><el-tag size="small">诊断</el-tag></div>
          <div class="info-row"><span class="info-label">AI模型：</span>GPT-4o</div>
          <div class="info-row"><span class="info-label">状态：</span><el-tag type="success" size="small">启用</el-tag></div>
          <div class="info-row"><span class="info-label">描述：</span>根据对话内容生成情绪诊断报告</div>
        </div>
      </div>

      <div class="detail-card">
        <div class="card-title">⚙️ 模型参数</div>
        <div class="card-body">
          <div class="info-row"><span class="info-label">温度（Temperature）：</span>0.7</div>
          <div class="info-row"><span class="info-label">最大Token数：</span>2048</div>
          <div class="info-row"><span class="info-label">Top-P：</span>0.9</div>
          <div class="info-row"><span class="info-label">频率惩罚：</span>0.0</div>
          <div class="info-row"><span class="info-label">存在惩罚：</span>0.0</div>
        </div>
      </div>
    </div>

    <div class="detail-card" style="margin-top: 14px">
      <div class="card-title">📝 Prompt 模板</div>
      <div class="card-body">
        <div class="prompt-block">
          <div class="prompt-label">System Prompt：</div>
          <div class="prompt-text">你是一位专业的心理健康评估AI助手。请根据用户的对话内容，分析其情绪状态、心理特征，并生成结构化的诊断报告。报告需包含：核心情绪、情绪占比、触发因素、风险评估、PAD维度分析及建议。</div>
        </div>
        <div class="prompt-block" style="margin-top: 12px">
          <div class="prompt-label">User Prompt 模板：</div>
          <div class="prompt-text">以下是用户在第{{round}}轮对话中的内容：\n{{conversation_content}}\n\n请基于以上内容生成诊断分析。</div>
        </div>
      </div>
    </div>

    <div class="detail-card" style="margin-top: 14px">
      <div class="card-title">🔗 上下游节点</div>
      <div class="card-body">
        <div class="info-row"><span class="info-label">上游节点：</span>焦虑评估节点、症状映射节点</div>
        <div class="info-row"><span class="info-label">下游节点：</span>建议生成节点</div>
      </div>
    </div>

    <div style="margin-top: 16px; display: flex; gap: 10px">
      <el-button type="primary">编辑节点</el-button>
      <el-button type="danger">删除节点</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { useRoute } from "vue-router";

const route = useRoute();
const nodeId = route.params.id as string;
const nodeName = ref("情绪诊断节点");
</script>

<style scoped>
.ai-node-detail-page {
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

.prompt-block {
  background: #f8f8fa;
  border-radius: 6px;
  padding: 12px;
}

.prompt-label {
  font-weight: 600;
  color: #666;
  margin-bottom: 6px;
}

.prompt-text {
  font-size: 12px;
  color: #333;
  line-height: 1.6;
  white-space: pre-wrap;
}
</style>