<template>
  <div class="ai-node-page">
    <div class="page-header">
      <h2 class="page-title">AI节点配置</h2>
      <el-button type="primary" @click="showAddDialog = true">+ 新增节点</el-button>
    </div>

    <el-table :data="nodeList" stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="节点名称" width="180" />
      <el-table-column prop="group" label="分组" width="100">
        <template #default="{ row }">
          <el-tag size="small">{{ row.group }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="model" label="AI模型" width="120" />
      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === '启用' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdTime" label="创建时间" width="180" />
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button type="primary" text size="small" @click="handleViewDetail(row)">详情</el-button>
          <el-button type="danger" text size="small">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-bar">
      <el-pagination v-model:current-page="currentPage" :page-size="DEFAULT_PAGE_SIZE" :total="5" :layout="PAGINATION_LAYOUT" background />
    </div>

    <el-dialog v-model="showAddDialog" title="新增AI节点" width="560">
      <el-form label-width="80px">
        <el-form-item label="节点名称"><el-input placeholder="请输入节点名称" /></el-form-item>
        <el-form-item label="分组">
          <el-select placeholder="请选择分组" style="width: 100%">
            <el-option v-for="g in AI_NODE_GROUPS" :key="g.value" :label="g.label" :value="g.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="AI模型">
          <el-select placeholder="请选择模型" style="width: 100%">
            <el-option v-for="m in AI_MODEL_TYPES" :key="m.value" :label="m.label" :value="m.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述"><el-input type="textarea" :rows="3" placeholder="请输入描述" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="showAddDialog = false">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";
import { DEFAULT_PAGE_SIZE, PAGINATION_LAYOUT, AI_NODE_GROUPS, AI_MODEL_TYPES } from "@/shared/api/config";

const router = useRouter();
const currentPage = ref(1);
const showAddDialog = ref(false);

const nodeList = ref([
  { id: 1, name: "情绪诊断节点", group: "诊断", model: "GPT-4o", description: "根据对话内容生成情绪诊断报告", status: "启用", createdTime: "2026-08-01 10:00" },
  { id: 2, name: "焦虑评估节点", group: "评估", model: "GPT-4o", description: "评估用户焦虑程度及变化趋势", status: "启用", createdTime: "2026-08-01 10:05" },
  { id: 3, name: "症状映射节点", group: "映射", model: "GLM-4", description: "将情绪分析结果映射到症状字典", status: "启用", createdTime: "2026-08-05 14:00" },
  { id: 4, name: "建议生成节点", group: "诊断", model: "Claude-3", description: "根据诊断结果生成个性化建议", status: "启用", createdTime: "2026-08-10 09:30" },
  { id: 5, name: "风险预警节点", group: "评估", model: "GPT-4o", description: "评估自伤/自杀风险等级", status: "禁用", createdTime: "2026-08-15 16:00" },
]);

function handleViewDetail(row: any) {
  router.push(`/admin/ai-node/${row.id}`);
}
</script>

<style scoped>
.ai-node-page {
  background: #fff;
  border-radius: 10px;
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: #333;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>