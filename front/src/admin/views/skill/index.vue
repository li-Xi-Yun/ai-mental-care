<template>
  <div class="skill-page">
    <div class="page-header">
      <h2 class="page-title">Skill管理</h2>
      <el-button type="primary" @click="showAddDialog = true">+ 新增Skill</el-button>
    </div>

    <el-table :data="skillList" stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="Skill名称" width="180" />
      <el-table-column prop="type" label="类型" width="120">
        <template #default="{ row }">
          <el-tag size="small">{{ row.type }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === '启用' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdTime" label="创建时间" width="180" />
      <el-table-column label="操作" width="180">
        <template #default>
          <el-button type="primary" text size="small">编辑</el-button>
          <el-button type="danger" text size="small">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-bar">
      <el-pagination v-model:current-page="currentPage" :page-size="DEFAULT_PAGE_SIZE" :total="4" :layout="PAGINATION_LAYOUT" background />
    </div>

    <el-dialog v-model="showAddDialog" title="新增Skill" width="560">
      <el-form label-width="80px">
        <el-form-item label="名称"><el-input placeholder="请输入Skill名称" /></el-form-item>
        <el-form-item label="类型">
          <el-select placeholder="请选择类型" style="width: 100%">
            <el-option label="对话增强" value="conversation" />
            <el-option label="诊断辅助" value="diagnosis" />
            <el-option label="情绪分析" value="emotion" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述"><el-input type="textarea" :rows="3" placeholder="请输入描述" /></el-form-item>
        <el-form-item label="Prompt"><el-input type="textarea" :rows="4" placeholder="请输入Prompt模板" /></el-form-item>
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
import { DEFAULT_PAGE_SIZE, PAGINATION_LAYOUT } from "@/shared/api/config";

const currentPage = ref(1);
const showAddDialog = ref(false);

const skillList = ref([
  { id: 1, name: "情绪识别Skill", type: "情绪分析", description: "从对话文本中识别用户情绪类型及强度", status: "启用", createdTime: "2026-08-01 10:00" },
  { id: 2, name: "共情回复Skill", type: "对话增强", description: "生成具有共情能力的回复内容", status: "启用", createdTime: "2026-08-05 14:00" },
  { id: 3, name: "风险筛查Skill", type: "诊断辅助", description: "筛查对话中的自伤/自杀风险信号", status: "启用", createdTime: "2026-08-10 09:30" },
  { id: 4, name: "放松引导Skill", type: "对话增强", description: "引导用户进行呼吸放松和正念冥想", status: "禁用", createdTime: "2026-08-15 16:00" },
]);
</script>

<style scoped>
.skill-page {
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