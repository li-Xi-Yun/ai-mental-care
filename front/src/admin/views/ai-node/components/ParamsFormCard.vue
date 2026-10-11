<template>
  <div class="info-grid">
    <div class="card">
      <div class="card-head">
        <el-icon><Setting /></el-icon>
        <h3>生成参数</h3>
        <span class="card-tip">Temperature / Tokens / 采样</span>
      </div>
      <el-form label-position="top" class="form-grid cols-3">
        <el-form-item label="温度 Temperature">
          <el-input-number v-model="form.temperature" :min="0" :max="2" :step="0.1" :precision="2" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="最大输出 Token 数">
          <el-input-number v-model="form.maxToken" :min="1" :max="32768" :step="64" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="Top-P 核采样">
          <el-input-number v-model="form.topP" :min="0" :max="1" :step="0.05" :precision="2" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="Top-K">
          <el-input-number v-model="form.topK" :min="1" :max="500" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="频率惩罚 Frequency Penalty">
          <el-input-number v-model="form.frequencyPenalty" :min="-2" :max="2" :step="0.1" :precision="2" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="存在惩罚 Presence Penalty">
          <el-input-number v-model="form.presencePenalty" :min="-2" :max="2" :step="0.1" :precision="2" controls-position="right" style="width: 100%" />
        </el-form-item>
      </el-form>
    </div>

    <div class="card">
      <div class="card-head">
        <el-icon><RefreshRight /></el-icon>
        <h3>重试策略</h3>
        <span class="card-tip">指数退避重试</span>
      </div>
      <el-form label-position="top" class="form-grid cols-3">
        <el-form-item label="最大重试次数">
          <el-input-number v-model="form.retryMaxAttempts" :min="0" :max="10" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="初始间隔（毫秒）">
          <el-input-number v-model="form.retryDelay" :min="0" :max="60000" :step="100" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="间隔乘数">
          <el-input-number v-model="form.retryMultiplier" :min="1" :max="10" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item class="span-3">
          <div class="form-hint">第 N 次重试等待间隔约 = 初始间隔 × 乘数^(N-1)，超出最大次数后放弃本次调用。</div>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { NodeFormState } from "@/admin/views/ai-node/useAiNodeDetail";

defineProps<{
  form: NodeFormState;
}>();
</script>

<style scoped>
.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

.card {
  background: #ffffff;
  border: 1px solid var(--admin-border);
  border-radius: var(--admin-radius-card);
  padding: 20px 22px;
  box-shadow: var(--admin-shadow-card);
}

.card-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #6366f1;
  padding-bottom: 12px;
  margin-bottom: 14px;
  border-bottom: 1px solid var(--admin-border);
}

.card-head h3 {
  font-size: 15px;
  font-weight: 600;
  color: #1e293b;
  margin: 0;
}

.card-tip {
  flex: 1;
  text-align: right;
  font-size: 12px;
  font-weight: 400;
  color: var(--admin-text-muted);
}

.form-grid {
  display: grid;
  gap: 0 16px;
}

.form-grid.cols-3 {
  grid-template-columns: 1fr 1fr 1fr;
}

.form-grid .span-3 {
  grid-column: span 3;
}

.form-grid .el-form-item {
  margin-bottom: 14px;
}

.form-hint {
  font-size: 12px;
  color: var(--admin-text-muted);
  line-height: 1.6;
  background: #f8fafc;
  border: 1px dashed #e2e8f0;
  border-radius: 8px;
  padding: 8px 12px;
  width: 100%;
}
</style>