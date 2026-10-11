<template>
  <div class="card">
    <div class="card-head">
      <el-icon><Document /></el-icon>
      <h3>基本信息</h3>
    </div>
    <el-form label-position="top" class="form-grid cols-2">
      <el-form-item label="节点名称">
        <el-input v-model="form.nodeName" placeholder="请输入节点名称" maxlength="50" />
      </el-form-item>
      <el-form-item label="节点标识 nodeKey">
        <el-input :model-value="nodeKey || ''" disabled />
      </el-form-item>
      <el-form-item label="分组">
        <el-input v-model="form.nodeGroup" placeholder="如 diagnosis / process" maxlength="50" />
      </el-form-item>
      <el-form-item label="启用状态">
        <el-switch
          v-model="form.enabled"
          :active-value="1"
          :inactive-value="0"
          active-text="启用"
          inactive-text="禁用"
        />
      </el-form-item>
      <el-form-item label="排序序号">
        <el-input-number v-model="form.sort" :min="0" :max="999" controls-position="right" style="width: 100%" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="节点用途备注" maxlength="255" show-word-limit />
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import type { NodeFormState } from "@/admin/views/ai-node/useAiNodeDetail";

defineProps<{
  form: NodeFormState;
  nodeKey?: string | null;
}>();
</script>

<style scoped>
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

.form-grid {
  display: grid;
  gap: 0 16px;
}

.form-grid.cols-2 {
  grid-template-columns: 1fr 1fr;
}

.form-grid .el-form-item {
  margin-bottom: 14px;
}
</style>