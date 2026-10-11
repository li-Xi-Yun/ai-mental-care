<template>
  <div class="scale-detail-page">
    <!-- ==================== 加载态 ==================== -->
    <template v-if="pageLoading">
      <el-skeleton :rows="6" animated class="page-state" />
    </template>

    <!-- ==================== 失败态 ==================== -->
    <div v-else-if="pageError || !detail" class="page-state">
      <el-empty description="量表详情加载失败">
        <el-button type="primary" @click="handleReload">重新加载</el-button>
      </el-empty>
    </div>

    <template v-else>
      <div class="detail-layout">
        <!-- ==================== 左侧：量表信息卡 ==================== -->
        <ScaleInfoPanel
          :detail="detail"
          :scale-name="scaleName"
          :scale-description="scaleDescription"
          :current-version="currentVersion"
          :version-count="versions.length"
          :loading="pageLoading"
          :format-time="formatTime"
          @reload="handleReload"
          @edit="openScaleEdit"
        />

        <!-- ==================== 右侧：Tab 主区 ==================== -->
        <section class="main-panel">
          <el-tabs v-model="activeTab" class="scale-tabs">
            <!-- 版本管理 -->
            <el-tab-pane name="version">
              <template #label>
                <span class="tab-label"><el-icon><Tickets /></el-icon>版本管理</span>
              </template>
              <VersionTab
                :scale-id="scaleId"
                :versions="versions"
                :page-loading="pageLoading"
                :format-time="formatTime"
                @changed="handleVersionsChanged"
              />
            </el-tab-pane>

            <!-- 维度管理 -->
            <el-tab-pane name="dimension">
              <template #label>
                <span class="tab-label"><el-icon><Grid /></el-icon>维度管理</span>
              </template>
              <DimensionTab
                v-model:version-id="selectedVersionId"
                :versions="versions"
                :selected-version="selectedVersion"
                :dimensions="dimensionList"
                :loading="dimensionLoading"
                @changed="handleDimensionsChanged"
                @reload-dimensions="reloadDimensions"
              />
            </el-tab-pane>

            <!-- 题目管理 -->
            <el-tab-pane name="question">
              <template #label>
                <span class="tab-label"><el-icon><List /></el-icon>题目管理</span>
              </template>
              <QuestionTab
                v-model:version-id="selectedVersionId"
                :versions="versions"
                :selected-version="selectedVersion"
                :dimensions="dimensionList"
                :questions="questionList"
                :loading="questionLoading"
                @changed="handleQuestionsChanged"
                @question-copied="handleQuestionCopied"
              />
            </el-tab-pane>

            <!-- 常模管理 -->
            <el-tab-pane name="norm">
              <template #label>
                <span class="tab-label"><el-icon><Odometer /></el-icon>常模管理</span>
              </template>
              <NormTab
                v-model:version-id="selectedVersionId"
                :versions="versions"
                :selected-version="selectedVersion"
                :dimensions="dimensionList"
              />
            </el-tab-pane>

            <!-- 结果规则 -->
            <el-tab-pane name="result-rule">
              <template #label>
                <span class="tab-label"><el-icon><DataBoard /></el-icon>结果规则</span>
              </template>
              <ResultRuleTab
                v-model:version-id="selectedVersionId"
                :versions="versions"
                :selected-version="selectedVersion"
                :dimensions="dimensionList"
              />
            </el-tab-pane>

            <!-- 分支规则 -->
            <el-tab-pane name="branch-rule">
              <template #label>
                <span class="tab-label"><el-icon><Connection /></el-icon>分支规则</span>
              </template>
              <BranchRuleTab
                v-model:version-id="selectedVersionId"
                :versions="versions"
                :selected-version="selectedVersion"
                :questions="questionList"
              />
            </el-tab-pane>

            <!-- 选项模板 -->
            <el-tab-pane name="optionTemplate">
              <template #label>
                <span class="tab-label"><el-icon><Menu /></el-icon>选项模板</span>
              </template>
              <OptionTemplateTab
                v-model:version-id="selectedVersionId"
                :versions="versions"
                :selected-version="selectedVersion"
                :questions="questionList"
                :format-time="formatTime"
                @questions-changed="reloadQuestions"
              />
            </el-tab-pane>
          </el-tabs>
        </section>
      </div>

      <!-- ==================== 量表信息编辑弹窗 ==================== -->
      <ScaleInfoDialog
        v-model="scaleEditVisible"
        :detail="detail"
        :categories="categoryList"
        :submitting="scaleEditSubmitting"
        @submit="handleScaleSubmit"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import { Connection, DataBoard, Grid, List, Menu, Odometer, Tickets } from "@element-plus/icons-vue";
import { useScaleDetail } from "./useScaleDetail";
import ScaleInfoPanel from "./components/ScaleInfoPanel.vue";
import ScaleInfoDialog from "./components/ScaleInfoDialog.vue";
import VersionTab from "./components/VersionTab.vue";
import DimensionTab from "./components/DimensionTab.vue";
import QuestionTab from "./components/QuestionTab.vue";
import NormTab from "./components/NormTab.vue";
import ResultRuleTab from "./components/ResultRuleTab.vue";
import BranchRuleTab from "./components/BranchRuleTab.vue";
import OptionTemplateTab from "./components/OptionTemplateTab.vue";

const {
  scaleId,
  pageLoading,
  pageError,
  detail,
  activeTab,
  scaleName,
  versions,
  currentVersion,
  scaleDescription,
  selectedVersionId,
  selectedVersion,
  dimensionList,
  dimensionLoading,
  questionList,
  questionLoading,
  categoryList,
  formatTime,
  handleReload,
  handleVersionsChanged,
  handleDimensionsChanged,
  handleQuestionsChanged,
  handleQuestionCopied,
  reloadDimensions,
  reloadQuestions,
  scaleEditVisible,
  scaleEditSubmitting,
  openScaleEdit,
  handleScaleSubmit,
} = useScaleDetail();
</script>

<style scoped>
.scale-detail-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
  color: var(--admin-text-primary);
}

/* ==================== 页面状态 ==================== */
.page-state {
  background: #fff;
  border: 1px solid var(--admin-border);
  border-radius: var(--admin-radius-card);
  padding: 40px 24px;
}

/* ==================== 主体布局 ==================== */
.detail-layout {
  display: flex;
  align-items: flex-start;
  gap: 20px;
}

/* ==================== 右侧主区 ==================== */
.main-panel {
  flex: 1;
  min-width: 0;
  background: #fff;
  border: 1px solid var(--admin-border);
  border-radius: var(--admin-radius-card);
  box-shadow: var(--admin-shadow-card);
  padding: 8px 16px 16px;
}

/* Tab 激活色 / 下划线由 Element Plus 全局主题（--el-color-primary）提供，不再页面覆写 */
.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.tab-label .el-icon {
  font-size: 15px;
  opacity: 0.7;
}
</style>
