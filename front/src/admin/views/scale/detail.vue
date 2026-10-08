<template>
  <div class="scale-detail-page">
    <!-- ==================== 面包屑 ==================== -->
    <div class="page-breadcrumb">
      <router-link to="/admin/scale" class="breadcrumb-link">量表管理</router-link>
      <span class="breadcrumb-sep">›</span>
      <span class="breadcrumb-current">{{ scaleName }}</span>
    </div>

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
        <aside class="info-panel">
          <div class="panel-head">
            <div class="panel-title">
              <el-icon class="panel-title-icon"><Collection /></el-icon>
              <span>量表信息</span>
            </div>
            <div class="panel-actions">
              <el-button text class="mini-btn" :loading="pageLoading" @click="handleReload">
                <el-icon><RefreshRight /></el-icon>
              </el-button>
              <el-button text class="mini-btn" @click="openScaleEdit">
                <el-icon><Edit /></el-icon>编辑
              </el-button>
            </div>
          </div>

          <div class="panel-body">
            <div class="scale-head">
              <h2 class="scale-title">{{ scaleName }}</h2>
              <el-tag :type="detail.status === 1 ? 'success' : 'danger'" size="small" effect="light">
                {{ detail.status === 1 ? "启用" : "禁用" }}
              </el-tag>
            </div>

            <div class="curr-version" v-if="currentVersion">
              <span class="cv-label">当前版本</span>
              <span class="cv-value">{{ currentVersion.versionNo }}</span>
              <el-tag v-if="currentVersion.isCurrent" type="success" size="small" effect="light">生效中</el-tag>
            </div>

            <div class="stat-grid">
              <div class="stat-item">
                <div class="stat-num">{{ detail.questionCount ?? 0 }}</div>
                <div class="stat-label">题目数</div>
              </div>
              <div class="stat-item">
                <div class="stat-num">{{ detail.dimensionCount ?? 0 }}</div>
                <div class="stat-label">维度数</div>
              </div>
              <div class="stat-item">
                <div class="stat-num">{{ versions.length }}</div>
                <div class="stat-label">版本数</div>
              </div>
            </div>

            <div class="info-block" v-if="scaleDescription">
              <div class="info-block-label">量表描述 / 指导语</div>
              <p class="info-block-text">{{ scaleDescription }}</p>
            </div>

            <div class="info-meta">
              <div class="meta-row">
                <span class="meta-label">分类</span>
                <span class="meta-value" v-if="detail.scaleCategoryName">{{ detail.scaleCategoryName }}</span>
                <span class="meta-value muted" v-else>未分类</span>
              </div>
              <div class="meta-row">
                <span class="meta-label">创建人</span>
                <span class="meta-value">{{ detail.createdByName || "—" }}</span>
              </div>
              <div class="meta-row">
                <span class="meta-label">创建时间</span>
                <span class="meta-value">{{ formatTime(detail.createdTime) }}</span>
              </div>
              <div class="meta-row">
                <span class="meta-label">最近更新</span>
                <span class="meta-value">{{ formatTime(detail.updatedTime) }}</span>
              </div>
            </div>
          </div>
        </aside>

        <!-- ==================== 右侧：Tab 主区 ==================== -->
        <section class="main-panel">
          <el-tabs v-model="activeTab" class="scale-tabs">
            <!-- 版本管理 -->
            <el-tab-pane name="version">
              <template #label>
                <span class="tab-label"><el-icon><Tickets /></el-icon>版本管理</span>
              </template>

              <div class="pane-toolbar">
                <div class="pane-title">
                  <span>版本列表</span>
                  <el-tag size="small" effect="plain">{{ versions.length }} 个版本</el-tag>
                </div>
                <div class="toolbar-spacer" />
                <el-button type="primary" @click="openAddVersion">
                  <el-icon class="btn-icon"><Plus /></el-icon>新增版本
                </el-button>
              </div>

              <el-table v-loading="pageLoading" :data="versions" class="pane-table" row-key="id">
                <el-table-column label="版本号" width="120">
                  <template #default="{ row }">
                    <span class="ver-no">{{ row.versionNo }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="说明" min-width="180" show-overflow-tooltip>
                  <template #default="{ row }">
                    <span v-if="row.description" class="cell-text">{{ row.description }}</span>
                    <span v-else class="muted">—</span>
                  </template>
                </el-table-column>
                <el-table-column label="题目数" width="84" align="center">
                  <template #default="{ row }">
                    <span class="num-text">{{ row.questionCount ?? 0 }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="维度数" width="84" align="center">
                  <template #default="{ row }">
                    <span class="num-text">{{ row.dimensionCount ?? 0 }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="是否当前" width="96" align="center">
                  <template #default="{ row }">
                    <el-tag v-if="row.isCurrent" type="success" size="small" effect="light">当前生效</el-tag>
                    <span v-else class="muted">—</span>
                  </template>
                </el-table-column>
                <el-table-column label="创建时间" width="140">
                  <template #default="{ row }">
                    <span class="time-text">{{ formatTime(row.createdTime) }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="创建人" width="96">
                  <template #default="{ row }">
                    <span>{{ row.createdByName || "—" }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="200" align="center" fixed="right">
                  <template #default="{ row }">
                    <el-button text size="small" class="op-btn op-primary" :loading="publishingId === row.id" @click="handlePublishVersion(row)">
                      <el-icon class="op-icon"><Promotion /></el-icon>发布
                    </el-button>
                    <el-button text size="small" class="op-btn op-edit" @click="openCopyVersion(row)">
                      <el-icon class="op-icon"><CopyDocument /></el-icon>复制
                    </el-button>
                    <el-button text size="small" class="op-btn op-del" @click="handleDeleteVersion(row)">
                      <el-icon class="op-icon"><Delete /></el-icon>删除
                    </el-button>
                  </template>
                </el-table-column>
                <template #empty>
                  <el-empty description="该量表暂无版本，请先新增版本" :image-size="70" />
                </template>
              </el-table>
            </el-tab-pane>

            <!-- 维度管理 -->
            <el-tab-pane name="dimension">
              <template #label>
                <span class="tab-label"><el-icon><Grid /></el-icon>维度管理</span>
              </template>

              <div class="pane-toolbar">
                <div class="version-switch">
                  <span class="switch-label">编辑版本</span>
                  <el-radio-group v-if="versions.length > 1" :model-value="selectedVersionId ?? undefined" size="small" @change="onVersionChange">
                    <el-radio-button v-for="v in versions" :key="v.id" :value="v.id">
                      {{ v.versionNo }}{{ v.isCurrent ? "·当前" : "" }}
                    </el-radio-button>
                  </el-radio-group>
                  <span v-else-if="versions.length === 1" class="switch-single">{{ versions[0].versionNo }}</span>
                  <span v-else class="muted">暂无版本</span>
                </div>
                <div class="toolbar-spacer" />
                <el-button type="primary" :disabled="!selectedVersion" @click="openAddDimension">
                  <el-icon class="btn-icon"><Plus /></el-icon>新增维度
                </el-button>
              </div>

              <el-table v-loading="dimensionLoading" :data="dimensionList" class="pane-table" row-key="id">
                <el-table-column label="维度名称" min-width="150" show-overflow-tooltip>
                  <template #default="{ row }">
                    <span class="dim-name">{{ row.dimName }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="编码" width="110">
                  <template #default="{ row }">
                    <code class="dim-code">{{ row.dimCode || "—" }}</code>
                  </template>
                </el-table-column>
                <el-table-column label="说明" min-width="170" show-overflow-tooltip>
                  <template #default="{ row }">
                    <span v-if="row.dimDesc" class="cell-text">{{ row.dimDesc }}</span>
                    <span v-else class="muted">—</span>
                  </template>
                </el-table-column>
                <el-table-column label="排序" width="120" align="center">
                  <template #default="{ row }">
                    <div class="sort-actions">
                      <el-button
                        text
                        size="small"
                        class="sort-btn"
                        :disabled="isDimFirst(row)"
                        @click="handleMoveDimension(row, -1)"
                      >
                        <el-icon><CaretTop /></el-icon>
                      </el-button>
                      <span class="sort-num">{{ row.sort ?? 0 }}</span>
                      <el-button
                        text
                        size="small"
                        class="sort-btn"
                        :disabled="isDimLast(row)"
                        @click="handleMoveDimension(row, 1)"
                      >
                        <el-icon><CaretBottom /></el-icon>
                      </el-button>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="题数" width="70" align="center">
                  <template #default="{ row }">
                    <span class="num-text">{{ row.questionCount ?? 0 }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="150" align="center" fixed="right">
                  <template #default="{ row }">
                    <el-button text size="small" class="op-btn op-edit" @click="openEditDimension(row)">编辑</el-button>
                    <el-button text size="small" class="op-btn op-del" @click="handleDeleteDimension(row)">删除</el-button>
                  </template>
                </el-table-column>
                <template #empty>
                  <el-empty description="该版本暂无维度" :image-size="70" />
                </template>
              </el-table>
            </el-tab-pane>

            <!-- 题目管理 -->
            <el-tab-pane name="question">
              <template #label>
                <span class="tab-label"><el-icon><List /></el-icon>题目管理</span>
              </template>

              <div class="pane-toolbar">
                <div class="version-switch">
                  <span class="switch-label">编辑版本</span>
                  <el-radio-group v-if="versions.length > 1" :model-value="selectedVersionId ?? undefined" size="small" @change="onVersionChange">
                    <el-radio-button v-for="v in versions" :key="v.id" :value="v.id">
                      {{ v.versionNo }}{{ v.isCurrent ? "·当前" : "" }}
                    </el-radio-button>
                  </el-radio-group>
                  <span v-else-if="versions.length === 1" class="switch-single">{{ versions[0].versionNo }}</span>
                  <span v-else class="muted">暂无版本</span>
                </div>
                <div class="toolbar-spacer" />
                <el-button type="primary" :disabled="!selectedVersion" @click="openAddQuestion">
                  <el-icon class="btn-icon"><Plus /></el-icon>新增题目
                </el-button>
              </div>

              <el-table v-loading="questionLoading" :data="questionList" class="pane-table" row-key="id">
                <el-table-column label="序号" width="64" align="center">
                  <template #default="{ $index }">
                    <span class="num-text">{{ $index + 1 }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="题目" min-width="220" show-overflow-tooltip>
                  <template #default="{ row }">
                    <span class="q-title">{{ row.title }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="题型" width="84" align="center">
                  <template #default="{ row }">
                    <el-tag size="small" effect="plain" :type="questionTypeTag(row.questionType)">
                      {{ questionTypeText(row.questionType) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="所属维度" width="130" show-overflow-tooltip>
                  <template #default="{ row }">
                    <span v-if="row.dimensionName" class="dim-tag">{{ row.dimensionName }}</span>
                    <span v-else class="muted">—</span>
                  </template>
                </el-table-column>
                <el-table-column label="必答" width="70" align="center">
                  <template #default="{ row }">
                    <el-tag v-if="row.required === 1" type="danger" size="small" effect="light">必答</el-tag>
                    <el-tag v-else type="info" size="small" effect="light">选答</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="选项摘要" min-width="180" show-overflow-tooltip>
                  <template #default="{ row }">
                    <span v-if="optionSummary(row)" class="option-summary">{{ optionSummary(row) }}</span>
                    <span v-else class="muted">—</span>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="190" align="center" fixed="right">
                  <template #default="{ row }">
                    <el-button text size="small" class="op-btn op-edit" @click="openEditQuestion(row)">编辑</el-button>
                    <el-button text size="small" class="op-btn op-primary" @click="openCopyQuestion(row)">
                      <el-icon class="op-icon"><CopyDocument /></el-icon>复制
                    </el-button>
                    <el-button text size="small" class="op-btn op-del" @click="handleDeleteQuestion(row)">删除</el-button>
                  </template>
                </el-table-column>
                <template #empty>
                  <el-empty description="该版本暂无题目，请先新增题目" :image-size="70" />
                </template>
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </section>
      </div>

      <!-- ==================== 量表信息编辑弹窗 ==================== -->
      <el-dialog v-model="scaleEditVisible" title="编辑量表信息" width="600px" align-center :close-on-click-modal="false">
        <el-form ref="scaleFormRef" :model="scaleForm" :rules="scaleRules" label-width="100px">
          <el-form-item label="量表名称" prop="scaleName">
            <el-input v-model="scaleForm.scaleName" placeholder="例：SAS 焦虑自评量表" maxlength="60" show-word-limit clearable />
          </el-form-item>
          <el-form-item label="量表分类">
            <el-select v-model="scaleForm.scaleCategoryId" placeholder="请选择分类（可选）" clearable style="width: 100%">
              <el-option v-for="c in categoryList" :key="c.id" :label="c.categoryName" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-radio-group v-model="scaleForm.status">
              <el-radio-button :value="1">启用</el-radio-button>
              <el-radio-button :value="0">禁用</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-divider content-position="left">作答配置</el-divider>
          <el-form-item label="重复作答">
            <el-radio-group v-model="scaleForm.allowRepeat">
              <el-radio-button :value="1">允许</el-radio-button>
              <el-radio-button :value="0">禁止</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="scaleForm.allowRepeat === 1" label="冷却时长">
            <el-input-number v-model="scaleForm.coolMinutes" :min="0" :max="10080" :step="60" style="width: 200px" />
            <span class="unit-text">分钟</span>
          </el-form-item>
          <el-form-item label="作答限时">
            <el-input-number v-model="scaleForm.timeLimit" :min="0" :max="7200" :step="60" style="width: 200px" />
            <span class="unit-text">秒</span>
            <div class="form-tip">0 或留空表示不限时</div>
          </el-form-item>
          <el-form-item label="匿名测评">
            <el-radio-group v-model="scaleForm.anonymous">
              <el-radio-button :value="1">匿名</el-radio-button>
              <el-radio-button :value="0">实名</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="scaleEditVisible = false">取 消</el-button>
          <el-button type="primary" :loading="scaleEditSubmitting" @click="handleScaleSave">保 存</el-button>
        </template>
      </el-dialog>

      <!-- ==================== 新增版本弹窗 ==================== -->
      <el-dialog v-model="versionDialogVisible" title="新增版本" width="520px" align-center :close-on-click-modal="false">
        <el-form ref="versionFormRef" :model="versionForm" :rules="versionRules" label-width="90px">
          <el-form-item label="版本号" prop="versionNo">
            <el-input v-model="versionForm.versionNo" placeholder="如 v1.1" maxlength="20" clearable />
          </el-form-item>
          <el-form-item label="量表说明">
            <el-input v-model="versionForm.description" type="textarea" :rows="3" placeholder="指导语 / 量表说明" maxlength="500" show-word-limit />
          </el-form-item>
          <el-form-item label="版权信息">
            <el-input v-model="versionForm.copyrightInfo" placeholder="如 © 2026 某机构" maxlength="200" show-word-limit />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="versionDialogVisible = false">取 消</el-button>
          <el-button type="primary" :loading="versionSubmitting" @click="handleAddVersion">确 定</el-button>
        </template>
      </el-dialog>

      <!-- ==================== 复制版本弹窗 ==================== -->
      <el-dialog v-model="copyVersionVisible" title="复制版本" width="440px" align-center :close-on-click-modal="false">
        <el-alert
          v-if="copyVersionSource"
          :title="`将复制版本「${copyVersionSource.versionNo}」的全部题目 / 维度 / 选项至新版本`"
          type="info"
          :closable="false"
          class="copy-alert"
        />
        <el-form ref="copyVersionFormRef" :model="copyVersionForm" :rules="copyVersionRules" label-width="90px">
          <el-form-item label="新版本号" prop="newVersionNo">
            <el-input v-model="copyVersionForm.newVersionNo" placeholder="如 v1.2" maxlength="20" clearable />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="copyVersionVisible = false">取 消</el-button>
          <el-button type="primary" :loading="copyVersionSubmitting" @click="handleCopyVersion">开 始 复 制</el-button>
        </template>
      </el-dialog>

      <!-- ==================== 新增 / 编辑维度弹窗 ==================== -->
      <el-dialog
        v-model="dimensionDialogVisible"
        :title="dimensionForm.id == null ? '新增维度' : '编辑维度'"
        width="520px"
        align-center
        :close-on-click-modal="false"
      >
        <el-form ref="dimensionFormRef" :model="dimensionForm" :rules="dimensionRules" label-width="90px">
          <el-form-item label="维度名称" prop="dimName">
            <el-input v-model="dimensionForm.dimName" placeholder="如 焦虑" maxlength="30" show-word-limit clearable />
          </el-form-item>
          <el-form-item label="维度编码" prop="dimCode">
            <el-input v-model="dimensionForm.dimCode" placeholder="程序计分用，如 ANX" maxlength="20" show-word-limit clearable />
          </el-form-item>
          <el-form-item label="维度说明">
            <el-input v-model="dimensionForm.dimDesc" type="textarea" :rows="2" placeholder="选填" maxlength="200" show-word-limit />
          </el-form-item>
          <el-form-item label="排序">
            <el-input-number v-model="dimensionForm.sort" :min="0" :max="9999" style="width: 160px" />
            <div class="form-tip">数值越小越靠前，也可在列表中直接拖动调整</div>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="dimensionDialogVisible = false">取 消</el-button>
          <el-button type="primary" :loading="dimensionSubmitting" @click="handleDimensionSubmit">确 定</el-button>
        </template>
      </el-dialog>

      <!-- ==================== 新增 / 编辑题目弹窗 ==================== -->
      <el-dialog
        v-model="questionDialogVisible"
        :title="questionForm.id == null ? '新增题目' : '编辑题目'"
        width="760px"
        align-center
        :close-on-click-modal="false"
      >
        <el-form ref="questionFormRef" :model="questionForm" :rules="questionRules" label-width="90px">
          <el-form-item label="题干" prop="title">
            <el-input v-model="questionForm.title" placeholder="请输入题干" maxlength="200" show-word-limit clearable />
          </el-form-item>
          <el-form-item label="题型">
            <el-radio-group v-model="questionForm.questionType">
              <el-radio-button v-for="t in QUESTION_TYPE_OPTIONS" :key="t.value" :value="t.value">{{ t.label }}</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="所属维度">
            <el-select v-model="questionForm.dimensionId" placeholder="请选择维度（可选）" clearable style="width: 100%">
              <el-option v-for="d in dimensionList" :key="d.id" :label="d.dimName" :value="d.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="计分方式">
            <el-select v-model="questionForm.scoreType" style="width: 220px">
              <el-option v-for="s in SCORE_TYPE_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="必答">
            <el-switch v-model="questionForm.required" :active-value="1" :inactive-value="0" active-text="必答" inactive-text="选答" />
          </el-form-item>

          <el-form-item v-if="isChoiceType" label="选项">
            <div class="option-editor">
              <div class="option-editor-head">
                <span class="option-editor-title">选项配置</span>
                <span class="option-editor-tip">为每个选项设置文本与原始分值</span>
                <div class="toolbar-spacer" />
                <el-button size="small" type="primary" plain @click="addOptionRow">
                  <el-icon class="btn-icon"><Plus /></el-icon>添加选项
                </el-button>
              </div>
              <div v-if="optionRows.length" class="option-rows">
                <div v-for="(opt, i) in optionRows" :key="i" class="option-row">
                  <span class="option-idx">{{ i + 1 }}</span>
                  <el-input v-model="opt.optionText" placeholder="选项文本" maxlength="50" clearable />
                  <el-input-number v-model="opt.score" :min="0" placeholder="分值" style="width: 130px" />
                  <el-button text class="op-btn op-del" @click="removeOptionRow(i)">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </div>
              </div>
              <div v-else class="option-empty">暂无选项，点击「添加选项」开始配置</div>
            </div>
          </el-form-item>
          <div v-else class="form-tip" style="margin: -4px 0 0 90px">填空题型无需配置选项</div>
        </el-form>
        <template #footer>
          <el-button @click="questionDialogVisible = false">取 消</el-button>
          <el-button type="primary" :loading="questionSubmitting" @click="handleQuestionSubmit">确 定</el-button>
        </template>
      </el-dialog>

      <!-- ==================== 复制题目弹窗 ==================== -->
      <el-dialog v-model="questionCopyVisible" title="复制题目" width="440px" align-center :close-on-click-modal="false">
        <el-alert
          v-if="questionCopySource"
          :title="`将题目「${questionCopySource.title}」及其选项复制到目标版本`"
          type="info"
          :closable="false"
          class="copy-alert"
        />
        <el-form label-width="90px">
          <el-form-item label="目标版本">
            <el-select v-model="questionCopyVersionId" placeholder="请选择目标版本" style="width: 100%">
              <el-option v-for="v in versions" :key="v.id" :label="`${v.versionNo}${v.isCurrent ? '（当前）' : ''}`" :value="v.id" />
            </el-select>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="questionCopyVisible = false">取 消</el-button>
          <el-button type="primary" :loading="questionCopySubmitting" @click="handleQuestionCopy">确 定</el-button>
        </template>
      </el-dialog>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, shallowRef, watch } from "vue";
import {
  Collection,
  Tickets,
  Grid,
  List,
  Plus,
  Edit,
  RefreshRight,
  CaretTop,
  CaretBottom,
  CopyDocument,
  Delete,
  Promotion,
} from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import type { FormInstance, FormRules } from "element-plus";
import { getScaleDetail, updateScale } from "@/admin/api/scale/scale";
import {
  addScaleVersion,
  copyScaleVersion,
  publishScaleVersion,
  deleteScaleVersion,
} from "@/admin/api/scale/version";
import {
  getScaleDimensionList,
  addScaleDimension,
  updateScaleDimension,
  updateScaleDimensionSort,
  deleteScaleDimension,
} from "@/admin/api/scale/dimension";
import {
  getScaleQuestionList,
  addScaleQuestion,
  updateScaleQuestion,
  deleteScaleQuestion,
  copyScaleQuestion,
} from "@/admin/api/scale/question";
import { getScaleCategoryPage } from "@/admin/api/scale/category";
import type {
  AdminScaleDetailVO,
  AdminScaleDimensionVO,
  AdminScaleQuestionVO,
  AdminScaleVersionVO,
  AdminScaleCategoryVO,
  AdminScaleOptionItemDTO,
} from "@/admin/api/scale/types";

/* ==================== 路由参数 ==================== */
const route = useRoute();
const scaleId = Number(route.params.scaleId);

/* ==================== 常量 ==================== */
const QUESTION_TYPE_OPTIONS = [
  { label: "单选", value: 1 },
  { label: "多选", value: 2 },
  { label: "填空", value: 3 },
] as const;

const SCORE_TYPE_OPTIONS = [
  { label: "正向计分", value: 1 },
  { label: "反向计分", value: 2 },
  { label: "不计分", value: 0 },
] as const;

/* ==================== 页面状态 ==================== */
const pageLoading = ref(true);
const pageError = ref(false);
const detail = shallowRef<AdminScaleDetailVO | null>(null);

const activeTab = ref("version");

const scaleName = computed(() => detail.value?.scaleName || "量表详情");
const versions = computed<AdminScaleVersionVO[]>(() => detail.value?.versions ?? []);
const currentVersion = computed(() => versions.value.find((v) => v.isCurrent) ?? versions.value[0] ?? null);
const scaleDescription = computed(
  () => currentVersion.value?.description || detail.value?.scaleName || "",
);

const selectedVersionId = ref<number | null>(null);
const selectedVersion = computed(() => versions.value.find((v) => v.id === selectedVersionId.value) ?? null);

function onVersionChange(v: string | number | boolean | undefined) {
  selectedVersionId.value = v == null ? null : Number(v);
}

const dimensionList = ref<AdminScaleDimensionVO[]>([]);
const dimensionLoading = ref(false);
const questionList = ref<AdminScaleQuestionVO[]>([]);
const questionLoading = ref(false);

const categoryList = ref<AdminScaleCategoryVO[]>([]);

/* ==================== 工具函数 ==================== */
function formatTime(v?: string): string {
  return v ? v.replace("T", " ").slice(0, 16) : "—";
}

function questionTypeText(type?: number): string {
  return QUESTION_TYPE_OPTIONS.find((t) => t.value === type)?.label ?? "未知";
}

function questionTypeTag(type?: number): "primary" | "warning" | "info" {
  if (type === 1) return "primary";
  if (type === 2) return "warning";
  return "info";
}

function optionSummary(row: any): string {
  return (row.options ?? []).map((o: any) => `${o.optionText}(${o.score ?? 0})`).join("、");
}

const sortedDims = computed(() =>
  [...dimensionList.value].sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0) || a.id - b.id),
);

function isDimFirst(row: any): boolean {
  return sortedDims.value[0]?.id === row.id;
}

function isDimLast(row: any): boolean {
  return sortedDims.value[sortedDims.value.length - 1]?.id === row.id;
}

/* ==================== 数据加载 ==================== */
async function loadDetail() {
  pageLoading.value = true;
  pageError.value = false;
  try {
    const res = await getScaleDetail(scaleId);
    const data = res.data.data ?? null;
    detail.value = data;
    if (!data) {
      pageError.value = true;
      return;
    }
    const fallback = data.currentVersionId ?? data.versions?.[0]?.id ?? null;
    if (
      selectedVersionId.value == null ||
      !data.versions?.some((v: any) => v.id === selectedVersionId.value)
    ) {
      selectedVersionId.value = fallback;
    }
  } catch (e) {
    pageError.value = true;
    ElMessage.error((e as Error)?.message || "量表详情加载失败");
  } finally {
    pageLoading.value = false;
  }
}

async function loadCategories() {
  try {
    const res = await getScaleCategoryPage({ pageNum: 1, pageSize: 1000 });
    categoryList.value = (res.data.data as { records?: AdminScaleCategoryVO[] })?.records ?? [];
  } catch {
    categoryList.value = [];
  }
}

async function loadDimensions(versionId: number) {
  dimensionLoading.value = true;
  try {
    const res = await getScaleDimensionList(versionId);
    dimensionList.value = res.data.data ?? [];
  } catch (e) {
    dimensionList.value = [];
    ElMessage.error((e as Error)?.message || "维度列表加载失败");
  } finally {
    dimensionLoading.value = false;
  }
}

async function loadQuestions(versionId: number) {
  questionLoading.value = true;
  try {
    const res = await getScaleQuestionList(versionId);
    questionList.value = res.data.data ?? [];
  } catch (e) {
    questionList.value = [];
    ElMessage.error((e as Error)?.message || "题目列表加载失败");
  } finally {
    questionLoading.value = false;
  }
}

watch(selectedVersionId, (id) => {
  if (id == null) {
    dimensionList.value = [];
    questionList.value = [];
    return;
  }
  loadDimensions(id);
  loadQuestions(id);
});

async function handleReload() {
  await loadDetail();
}

/* ==================== 交互：量表信息编辑 ==================== */
const scaleEditVisible = ref(false);
const scaleEditSubmitting = ref(false);
const scaleFormRef = ref<FormInstance>();
const scaleForm = reactive({
  scaleName: "",
  scaleCategoryId: null as number | null,
  status: 1,
  allowRepeat: 1,
  coolMinutes: 1440,
  timeLimit: 0,
  anonymous: 0,
});
const scaleRules: FormRules = {
  scaleName: [{ required: true, message: "请输入量表名称", trigger: "blur" }],
};

function openScaleEdit() {
  const d = detail.value;
  if (!d) return;
  Object.assign(scaleForm, {
    scaleName: d.scaleName ?? "",
    scaleCategoryId: d.scaleCategoryId ?? null,
    status: d.status ?? 1,
    allowRepeat: d.allowRepeat ?? 1,
    coolMinutes: d.coolMinutes ?? 1440,
    timeLimit: d.timeLimit ?? 0,
    anonymous: d.anonymous ?? 0,
  });
  scaleEditVisible.value = true;
}

async function handleScaleSave() {
  const valid = await scaleFormRef.value?.validate().catch(() => false);
  if (!valid) return;
  scaleEditSubmitting.value = true;
  try {
    await updateScale(scaleId, {
      scaleName: scaleForm.scaleName.trim(),
      scaleCategoryId: scaleForm.scaleCategoryId,
      status: scaleForm.status,
      allowRepeat: scaleForm.allowRepeat,
      coolMinutes: scaleForm.allowRepeat === 1 ? scaleForm.coolMinutes : 0,
      timeLimit: scaleForm.timeLimit || null,
      anonymous: scaleForm.anonymous,
    });
    ElMessage.success("量表信息已更新");
    scaleEditVisible.value = false;
    await loadDetail();
  } catch (e) {
    ElMessage.error((e as Error)?.message || "保存失败，请稍后重试");
  } finally {
    scaleEditSubmitting.value = false;
  }
}

/* ==================== 交互：版本管理 ==================== */
const versionDialogVisible = ref(false);
const versionSubmitting = ref(false);
const versionFormRef = ref<FormInstance>();
const versionForm = reactive({ versionNo: "", description: "", copyrightInfo: "" });
const versionRules: FormRules = {
  versionNo: [{ required: true, message: "请输入版本号", trigger: "blur" }],
};

function openAddVersion() {
  versionForm.versionNo = "";
  versionForm.description = "";
  versionForm.copyrightInfo = "";
  versionDialogVisible.value = true;
}

async function handleAddVersion() {
  const valid = await versionFormRef.value?.validate().catch(() => false);
  if (!valid) return;
  versionSubmitting.value = true;
  try {
    await addScaleVersion({
      scaleId,
      versionNo: versionForm.versionNo.trim(),
      description: versionForm.description.trim() || undefined,
      copyrightInfo: versionForm.copyrightInfo.trim() || undefined,
    });
    ElMessage.success("版本已创建");
    versionDialogVisible.value = false;
    await loadDetail();
  } catch (e) {
    ElMessage.error((e as Error)?.message || "创建版本失败，请稍后重试");
  } finally {
    versionSubmitting.value = false;
  }
}

const copyVersionSource = ref<AdminScaleVersionVO | null>(null);
const copyVersionVisible = ref(false);
const copyVersionSubmitting = ref(false);
const copyVersionFormRef = ref<FormInstance>();
const copyVersionForm = reactive({ newVersionNo: "" });
const copyVersionRules: FormRules = {
  newVersionNo: [{ required: true, message: "请输入新版本号", trigger: "blur" }],
};

function openCopyVersion(row: any) {
  copyVersionSource.value = row;
  copyVersionForm.newVersionNo = "";
  copyVersionVisible.value = true;
}

async function handleCopyVersion() {
  const source = copyVersionSource.value;
  if (!source) return;
  const valid = await copyVersionFormRef.value?.validate().catch(() => false);
  if (!valid) return;
  copyVersionSubmitting.value = true;
  try {
    await copyScaleVersion({
      sourceVersionId: source.id,
      targetScaleId: scaleId,
      versionNo: copyVersionForm.newVersionNo.trim(),
    });
    ElMessage.success(`版本「${source.versionNo}」复制成功`);
    copyVersionVisible.value = false;
    await loadDetail();
  } catch (e) {
    ElMessage.error((e as Error)?.message || "复制版本失败，请稍后重试");
  } finally {
    copyVersionSubmitting.value = false;
  }
}

const publishingId = ref<number | null>(null);

async function handlePublishVersion(row: any) {
  const confirmText = (row.questionCount ?? 0) > 0
    ? `确定发布版本「${row.versionNo}」为当前生效版本吗？发布后测评将使用该版本。`
    : `版本「${row.versionNo}」下暂无题目，无法发布。请先添加题目。`;
  if ((row.questionCount ?? 0) <= 0) {
    ElMessage.warning(confirmText);
    return;
  }
  try {
    await ElMessageBox.confirm(`确定发布版本「${row.versionNo}」为当前生效版本吗？`, "发布确认", {
      type: "warning",
      confirmButtonText: "发布",
      cancelButtonText: "取消",
    });
  } catch {
    return;
  }
  publishingId.value = row.id;
  try {
    await publishScaleVersion(row.id);
    ElMessage.success(`版本「${row.versionNo}」已发布`);
    await loadDetail();
  } catch (e) {
    ElMessage.error((e as Error)?.message || "发布失败，请稍后重试");
  } finally {
    publishingId.value = null;
  }
}

async function handleDeleteVersion(row: any) {
  try {
    await ElMessageBox.confirm(
      `确定删除版本「${row.versionNo}」吗？该版本下的题目、维度、选项等将一并删除。`,
      "删除确认",
      { type: "warning", confirmButtonText: "删除", cancelButtonText: "取消", confirmButtonClass: "el-button--danger" },
    );
  } catch {
    return;
  }
  try {
    await deleteScaleVersion(row.id);
    ElMessage.success("版本已删除");
    await loadDetail();
  } catch (e) {
    ElMessage.error((e as Error)?.message || "删除失败，请稍后重试");
  }
}

/* ==================== 交互：维度管理 ==================== */
const dimensionDialogVisible = ref(false);
const dimensionSubmitting = ref(false);
const dimensionFormRef = ref<FormInstance>();
const dimensionForm = reactive({
  id: null as number | null,
  dimName: "",
  dimCode: "",
  dimDesc: "",
  sort: 0,
});
const dimensionRules: FormRules = {
  dimName: [{ required: true, message: "请输入维度名称", trigger: "blur" }],
  dimCode: [{ required: true, message: "请输入维度编码", trigger: "blur" }],
};

function maxDimSort(): number {
  return dimensionList.value.reduce((m, d) => Math.max(m, d.sort ?? 0), -1);
}

function openAddDimension() {
  Object.assign(dimensionForm, { id: null, dimName: "", dimCode: "", dimDesc: "", sort: maxDimSort() + 1 });
  dimensionDialogVisible.value = true;
}

function openEditDimension(row: any) {
  Object.assign(dimensionForm, {
    id: row.id,
    dimName: row.dimName,
    dimCode: row.dimCode ?? "",
    dimDesc: row.dimDesc ?? "",
    sort: row.sort ?? 0,
  });
  dimensionDialogVisible.value = true;
}

async function handleDimensionSubmit() {
  const vid = selectedVersionId.value;
  if (vid == null) {
    ElMessage.warning("请先选择量表版本");
    return;
  }
  const valid = await dimensionFormRef.value?.validate().catch(() => false);
  if (!valid) return;
  dimensionSubmitting.value = true;
  try {
    const payload = {
      scaleVersionId: vid,
      dimName: dimensionForm.dimName.trim(),
      dimCode: dimensionForm.dimCode.trim(),
      dimDesc: dimensionForm.dimDesc.trim() || undefined,
      sort: dimensionForm.sort,
    };
    if (dimensionForm.id == null) {
      await addScaleDimension(payload);
      ElMessage.success("维度已新增");
    } else {
      await updateScaleDimension(dimensionForm.id, payload);
      ElMessage.success("维度已更新");
    }
    dimensionDialogVisible.value = false;
    await Promise.all([loadDimensions(vid), loadQuestions(vid), loadDetail()]);
  } catch (e) {
    ElMessage.error((e as Error)?.message || "保存失败，请稍后重试");
  } finally {
    dimensionSubmitting.value = false;
  }
}

async function handleMoveDimension(row: any, dir: -1 | 1) {
  const list = sortedDims.value;
  const idx = list.findIndex((d) => d.id === row.id);
  const target = list[idx + dir];
  if (idx < 0 || !target) return;

  const tmp = row.sort ?? idx;
  row.sort = target.sort ?? idx + dir;
  target.sort = tmp;

  try {
    await Promise.all([
      updateScaleDimensionSort(row.id, row.sort ?? 0),
      updateScaleDimensionSort(target.id, target.sort ?? 0),
    ]);
    ElMessage.success("排序已更新");
  } catch (e) {
    ElMessage.error((e as Error)?.message || "排序更新失败，请稍后重试");
    const vid = selectedVersionId.value;
    if (vid != null) loadDimensions(vid);
  }
}

async function handleDeleteDimension(row: any) {
  try {
    await ElMessageBox.confirm(
      `确定删除维度「${row.dimName}」吗？该维度下存在题目时将禁止删除。`,
      "删除确认",
      { type: "warning", confirmButtonText: "删除", cancelButtonText: "取消", confirmButtonClass: "el-button--danger" },
    );
  } catch {
    return;
  }
  try {
    await deleteScaleDimension(row.id);
    ElMessage.success("维度已删除");
    const vid = selectedVersionId.value;
    if (vid != null) {
      await Promise.all([loadDimensions(vid), loadQuestions(vid)]);
    }
    await loadDetail();
  } catch (e) {
    ElMessage.error((e as Error)?.message || "删除失败，请稍后重试");
  }
}

/* ==================== 交互：题目管理 ==================== */
interface QuestionFormState {
  id: number | null;
  title: string;
  questionType: number;
  dimensionId: number | null;
  required: number;
  scoreType: number;
}

interface OptionRow {
  optionText: string;
  score: number | null;
}

const questionDialogVisible = ref(false);
const questionSubmitting = ref(false);
const questionFormRef = ref<FormInstance>();
const questionForm = reactive<QuestionFormState>({
  id: null,
  title: "",
  questionType: 1,
  dimensionId: null,
  required: 1,
  scoreType: 1,
});
const questionRules: FormRules = {
  title: [{ required: true, message: "请输入题干", trigger: "blur" }],
};
const optionRows = ref<OptionRow[]>([]);

const isChoiceType = computed(() => questionForm.questionType === 1 || questionForm.questionType === 2);

function openAddQuestion() {
  Object.assign(questionForm, { id: null, title: "", questionType: 1, dimensionId: null, required: 1, scoreType: 1 });
  optionRows.value = [];
  questionDialogVisible.value = true;
}

function openEditQuestion(row: any) {
  Object.assign(questionForm, {
    id: row.id,
    title: row.title,
    questionType: row.questionType ?? 1,
    dimensionId: row.dimensionId ?? null,
    required: row.required ?? 1,
    scoreType: row.scoreType ?? 1,
  });
  optionRows.value = (row.options ?? []).map((o: any) => ({ optionText: o.optionText ?? "", score: o.score ?? 0 }));
  questionDialogVisible.value = true;
}

function addOptionRow() {
  optionRows.value.push({ optionText: "", score: 0 });
}

function removeOptionRow(index: number) {
  optionRows.value.splice(index, 1);
}

function buildOptionPayload(): AdminScaleOptionItemDTO[] {
  return optionRows.value
    .filter((r) => r.optionText.trim())
    .map((r, i) => ({ optionText: r.optionText.trim(), score: r.score ?? 0, sort: i }));
}

async function handleQuestionSubmit() {
  const vid = selectedVersionId.value;
  if (vid == null) {
    ElMessage.warning("请先选择量表版本");
    return;
  }
  const valid = await questionFormRef.value?.validate().catch(() => false);
  if (!valid) return;
  if (isChoiceType.value && optionRows.value.filter((r) => r.optionText.trim()).length === 0) {
    ElMessage.warning("单选 / 多选题目至少需要一个选项");
    return;
  }
  questionSubmitting.value = true;
  try {
    const options = isChoiceType.value ? buildOptionPayload() : [];
    const payload = {
      scaleVersionId: vid,
      dimensionId: questionForm.dimensionId,
      title: questionForm.title.trim(),
      questionType: questionForm.questionType,
      required: questionForm.required,
      scoreType: questionForm.scoreType,
      sort: questionForm.id == null ? questionList.value.length : undefined,
      options,
    };
    if (questionForm.id == null) {
      await addScaleQuestion(payload);
      ElMessage.success("题目已新增");
    } else {
      await updateScaleQuestion(questionForm.id, payload);
      ElMessage.success("题目已更新");
    }
    questionDialogVisible.value = false;
    await Promise.all([loadQuestions(vid), loadDetail()]);
  } catch (e) {
    ElMessage.error((e as Error)?.message || "保存失败，请稍后重试");
  } finally {
    questionSubmitting.value = false;
  }
}

const questionCopySource = ref<AdminScaleQuestionVO | null>(null);
const questionCopyVisible = ref(false);
const questionCopySubmitting = ref(false);
const questionCopyVersionId = ref<number | null>(null);

function openCopyQuestion(row: any) {
  questionCopySource.value = row;
  questionCopyVersionId.value = selectedVersionId.value;
  questionCopyVisible.value = true;
}

async function handleQuestionCopy() {
  const source = questionCopySource.value;
  if (!source) return;
  const targetVid = questionCopyVersionId.value;
  if (targetVid == null) {
    ElMessage.warning("请选择目标版本");
    return;
  }
  questionCopySubmitting.value = true;
  try {
    await copyScaleQuestion(source.id, targetVid);
    ElMessage.success("题目已复制");
    questionCopyVisible.value = false;
    if (targetVid === selectedVersionId.value) {
      await loadQuestions(targetVid);
    }
    await loadDetail();
  } catch (e) {
    ElMessage.error((e as Error)?.message || "复制题目失败，请稍后重试");
  } finally {
    questionCopySubmitting.value = false;
  }
}

async function handleDeleteQuestion(row: any) {
  try {
    await ElMessageBox.confirm(
      `确定删除题目「${row.title}」吗？该题目的选项与相关跳题规则将被一并清理。`,
      "删除确认",
      { type: "warning", confirmButtonText: "删除", cancelButtonText: "取消", confirmButtonClass: "el-button--danger" },
    );
  } catch {
    return;
  }
  try {
    await deleteScaleQuestion(row.id);
    ElMessage.success("题目已删除");
    const vid = selectedVersionId.value;
    if (vid != null) {
      await Promise.all([loadQuestions(vid), loadDetail()]);
    }
  } catch (e) {
    ElMessage.error((e as Error)?.message || "删除失败，请稍后重试");
  }
}

/* ==================== 初始化 ==================== */
onMounted(() => {
  loadDetail();
  loadCategories();
});
</script>

<style scoped>
.scale-detail-page {
  --el-color-primary: #6366f1;
  --el-color-primary-light-3: #818cf8;
  --el-color-primary-light-5: #a5b4fc;
  --el-color-primary-light-7: #c7d2fe;
  --el-color-primary-light-8: #e0e7ff;
  --el-color-primary-light-9: #eef2ff;
  --el-color-primary-dark-2: #4338ca;
  --el-border-radius-base: 8px;

  color: #1e293b;
}

/* ==================== 面包屑 ==================== */
.page-breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #94a3b8;
  margin-bottom: 16px;
}
.breadcrumb-link {
  color: #6366f1;
  text-decoration: none;
  font-weight: 500;
  transition: color 0.2s ease;
}
.breadcrumb-link:hover {
  color: #4f46e5;
  text-decoration: underline;
}
.breadcrumb-sep {
  color: #cbd5e1;
}
.breadcrumb-current {
  color: #1e293b;
  font-weight: 600;
}

/* ==================== 页面状态 ==================== */
.page-state {
  background: #fff;
  border: 1px solid #eef1f6;
  border-radius: 12px;
  padding: 40px 24px;
}

/* ==================== 主体布局 ==================== */
.detail-layout {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}

/* ==================== 左侧量表信息卡 ==================== */
.info-panel {
  width: 302px;
  flex-shrink: 0;
  background: #fff;
  border: 1px solid #eef1f6;
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.04);
  overflow: hidden;
  position: sticky;
  top: 0;
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px 12px;
  border-bottom: 1px solid #f1f5f9;
}
.panel-title {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 14px;
  font-weight: 600;
  color: #334155;
}
.panel-title-icon {
  color: #6366f1;
  font-size: 16px;
}
.panel-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}
.mini-btn {
  color: #6366f1;
  font-size: 12px;
  padding: 4px 8px;
  height: auto;
}
.panel-body {
  padding: 16px;
}
.scale-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}
.scale-title {
  font-size: 19px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.3;
  margin: 0;
  word-break: break-all;
}
.curr-version {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #f8faff;
  border: 1px solid #e0e7ff;
  border-radius: 8px;
  margin-bottom: 14px;
}
.cv-label {
  font-size: 12px;
  color: #94a3b8;
}
.cv-value {
  font-size: 13px;
  font-weight: 600;
  color: #4f46e5;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin-bottom: 14px;
}
.stat-item {
  text-align: center;
  padding: 10px 4px;
  background: #f8fafc;
  border: 1px solid #eef1f6;
  border-radius: 8px;
}
.stat-num {
  font-size: 22px;
  font-weight: 700;
  color: #6366f1;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}
.stat-label {
  margin-top: 2px;
  font-size: 11px;
  color: #94a3b8;
}

.info-block {
  margin-bottom: 14px;
}
.info-block-label {
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
  margin-bottom: 4px;
}
.info-block-text {
  font-size: 12px;
  color: #64748b;
  line-height: 1.7;
  margin: 0;
  max-height: 120px;
  overflow-y: auto;
}

.info-meta {
  border-top: 1px dashed #e2e8f0;
  padding-top: 12px;
}
.meta-row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  margin-bottom: 7px;
}
.meta-label {
  width: 60px;
  flex-shrink: 0;
  color: #94a3b8;
}
.meta-value {
  color: #334155;
  word-break: break-all;
}

/* ==================== 右侧主区 ==================== */
.main-panel {
  flex: 1;
  min-width: 0;
  background: #fff;
  border: 1px solid #eef1f6;
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.04);
  padding: 8px 16px 16px;
}

.scale-tabs .el-tabs__item.is-active {
  color: #6366f1;
}
.scale-tabs .el-tabs__item:hover {
  color: #4f46e5;
}
.scale-tabs .el-tabs__active-bar {
  background-color: #6366f1;
}
.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.tab-label .el-icon {
  font-size: 15px;
  opacity: 0.7;
}

/* ==================== Tab 内工具栏 ==================== */
.pane-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin: 6px 0 14px;
}
.toolbar-spacer {
  flex: 1;
}
.pane-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}
.version-switch {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.switch-label {
  font-size: 12px;
  color: #94a3b8;
}
.switch-single {
  font-size: 13px;
  font-weight: 600;
  color: #4f46e5;
}
.btn-icon {
  margin-right: 4px;
}

/* ==================== 表格 ==================== */
.pane-table {
  --el-table-header-bg-color: #f8fafc;
  --el-table-header-text-color: #475569;
  --el-table-border-color: #eef1f6;
  --el-table-row-hover-bg-color: #f8faff;
  width: 100%;
  border-radius: 8px;
  overflow: hidden;
}
.pane-table :deep(.el-table__header th) {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.3px;
}
.pane-table :deep(.el-table__cell) {
  padding: 10px 0;
}

.cell-text {
  font-size: 13px;
  color: #475569;
}
.num-text {
  font-size: 13px;
  color: #475569;
  font-variant-numeric: tabular-nums;
}
.time-text {
  font-size: 12px;
  color: #94a3b8;
  font-variant-numeric: tabular-nums;
}
.muted {
  color: #cbd5e1;
  font-size: 12px;
}
.ver-no {
  font-weight: 600;
  color: #4f46e5;
  font-family: ui-monospace, "SFMono-Regular", Consolas, monospace;
  font-size: 13px;
}
.dim-name {
  font-weight: 600;
  color: #334155;
}
.dim-code {
  font-family: ui-monospace, "SFMono-Regular", Consolas, monospace;
  font-size: 12px;
  color: #6366f1;
  background: #eef2ff;
  border-radius: 4px;
  padding: 1px 6px;
}
.dim-tag {
  display: inline-block;
  padding: 2px 9px;
  border-radius: 999px;
  background: #eef2ff;
  color: #4f46e5;
  font-size: 11.5px;
  border: 1px solid #e0e7ff;
  line-height: 1.5;
}
.q-title {
  font-size: 13px;
  color: #1e293b;
  line-height: 1.5;
}
.option-summary {
  font-size: 12px;
  color: #64748b;
}

/* ==================== 操作按钮 ==================== */
.op-btn {
  font-size: 12px;
  font-weight: 500;
  padding: 0 6px;
}
.op-icon {
  margin-right: 3px;
  font-size: 12px;
}
.op-primary {
  color: #6366f1;
}
.op-primary:hover {
  color: #4f46e5;
}
.op-edit {
  color: #0ea5e9;
}
.op-edit:hover {
  color: #0284c7;
}
.op-del {
  color: #ef4444;
}
.op-del:hover {
  color: #dc2626;
}

/* ==================== 排序 ==================== */
.sort-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
}
.sort-btn {
  padding: 2px;
  height: auto;
}
.sort-num {
  min-width: 24px;
  font-size: 12px;
  color: #64748b;
  font-variant-numeric: tabular-nums;
}

/* ==================== 弹窗通用 ==================== */
.form-tip {
  font-size: 11.5px;
  color: #94a3b8;
  line-height: 1.5;
  margin-top: 4px;
}
.unit-text {
  margin-left: 8px;
  font-size: 12px;
  color: #64748b;
}
.copy-alert {
  margin-bottom: 14px;
}

/* ==================== 题目弹窗 ==================== */
.option-editor {
  width: 100%;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  overflow: hidden;
}
.option-editor-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #f8fafc;
  border-bottom: 1px solid #eef1f6;
}
.option-editor-title {
  font-size: 12px;
  font-weight: 600;
  color: #334155;
}
.option-editor-tip {
  font-size: 11.5px;
  color: #94a3b8;
}
.option-rows {
  padding: 10px 12px;
}
.option-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.option-row:last-child {
  margin-bottom: 0;
}
.option-idx {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  border-radius: 50%;
  background: #eef2ff;
  color: #6366f1;
  font-size: 11px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}
.option-empty {
  padding: 20px;
  text-align: center;
  font-size: 12px;
  color: #cbd5e1;
}
</style>