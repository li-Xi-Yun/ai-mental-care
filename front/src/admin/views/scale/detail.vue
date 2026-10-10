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

            <!-- 常模管理 -->
            <el-tab-pane name="norm">
              <template #label>
                <span class="tab-label"><el-icon><Odometer /></el-icon>常模管理</span>
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
                <el-button type="primary" :disabled="!selectedVersion" @click="openAddNormGroup">
                  <el-icon class="btn-icon"><Plus /></el-icon>新增常模组
                </el-button>
              </div>

              <div v-loading="normGroupLoading" class="norm-split">
                <div class="norm-group-pane">
                  <div class="norm-group-head">
                    <span class="norm-group-title">常模组</span>
                    <el-tag size="small" effect="plain">{{ normGroupList.length }} 组</el-tag>
                  </div>
                  <el-empty v-if="!normGroupList.length" description="该版本暂无常模组，请先新增" :image-size="70" />
                  <div v-else class="norm-group-list">
                    <div
                      v-for="g in normGroupList"
                      :key="g.id"
                      class="norm-group-item"
                      :class="{ active: selectedNormGroupId === g.id }"
                      @click="handleNormGroupRow(g)"
                    >
                      <div class="ngi-main">
                        <span class="ngi-name">{{ g.groupName }}</span>
                        <el-tag v-if="g.dimensionId == null" size="small" effect="light" class="ngi-tag">总分</el-tag>
                        <el-tag v-else size="small" effect="light" class="ngi-tag">{{ g.dimensionName || "维度" }}</el-tag>
                      </div>
                      <div class="ngi-sub">{{ normGroupAppliedInfo(g) }}</div>
                      <div class="ngi-meta">
                        <span v-if="g.normType === 0" class="ngi-stats">M {{ fmtNum(g.mean) }} / SD {{ fmtNum(g.sd) }}</span>
                        <span v-else class="ngi-stats">查表法 · {{ g.normCount ?? 0 }} 条</span>
                        <span class="ngi-sort">排序 {{ g.sort ?? 0 }}</span>
                      </div>
                      <div class="ngi-ops">
                        <el-button text size="small" class="op-btn op-edit" @click.stop="openEditNormGroup(g)">编辑</el-button>
                        <el-button text size="small" class="op-btn op-del" @click.stop="handleDeleteNormGroup(g)">删除</el-button>
                      </div>
                    </div>
                  </div>
                </div>

                <div class="norm-detail-pane">
                  <div v-if="!activeNormGroup" class="norm-placeholder">
                    <el-empty description="请选择左侧常模组以查看常模换算表" :image-size="80" />
                  </div>
                  <template v-else>
                    <div class="norm-detail-head">
                      <div class="ndh-title">
                        <span class="ndh-name">{{ activeNormGroup.groupName }}</span>
                        <span class="ndh-sub">常模换算表（原始分 → T/Z/百分位/标准九/DIQ）</span>
                      </div>
                      <div class="ndh-actions">
                        <el-button text size="small" class="op-btn op-edit" @click="openEditNormGroup(activeNormGroup)">
                          <el-icon class="op-icon"><Edit /></el-icon>编辑组信息
                        </el-button>
                        <el-button type="primary" size="small" :loading="normSaving" @click="handleNormRowsSave">批量保存</el-button>
                      </div>
                    </div>

                    <el-table v-loading="normLoading" :data="normRows" class="pane-table norm-table" row-key="id" height="430">
                      <el-table-column label="原始分" min-width="110">
                        <template #default="{ row }">
                          <el-input-number v-model="row.rawScore" :controls="false" :precision="2" :step="1" style="width: 100%" placeholder="原始分" />
                        </template>
                      </el-table-column>
                      <el-table-column label="T 分" min-width="105">
                        <template #default="{ row }">
                          <el-input-number v-model="row.tScore" :controls="false" :precision="2" style="width: 100%" placeholder="T 分" />
                        </template>
                      </el-table-column>
                      <el-table-column label="Z 分" min-width="105">
                        <template #default="{ row }">
                          <el-input-number v-model="row.zScore" :controls="false" :precision="2" style="width: 100%" placeholder="Z 分" />
                        </template>
                      </el-table-column>
                      <el-table-column label="百分位" min-width="105">
                        <template #default="{ row }">
                          <el-input-number v-model="row.percentile" :controls="false" :precision="2" :min="0" :max="100" style="width: 100%" placeholder="0~100" />
                        </template>
                      </el-table-column>
                      <el-table-column label="标准九" min-width="100">
                        <template #default="{ row }">
                          <el-input-number v-model="row.stanine" :controls="false" :min="1" :max="9" style="width: 100%" placeholder="1~9" />
                        </template>
                      </el-table-column>
                      <el-table-column label="DIQ" min-width="105">
                        <template #default="{ row }">
                          <el-input-number v-model="row.diq" :controls="false" :precision="2" style="width: 100%" placeholder="DIQ" />
                        </template>
                      </el-table-column>
                      <el-table-column label="等级标签" min-width="100">
                        <template #default="{ row }">
                          <el-select v-model="row.levelLabel" placeholder="—" clearable style="width: 100%">
                            <el-option v-for="o in LEVEL_LABEL_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                          </el-select>
                        </template>
                      </el-table-column>
                      <el-table-column label="操作" width="110" align="center">
                        <template #default="{ row, $index }">
                          <el-button text size="small" class="op-btn op-del" @click="removeNormRow($index)">移除</el-button>
                          <el-button
                            v-if="row.id != null"
                            text
                            size="small"
                            class="op-btn op-del"
                            @click="handleDeleteNormRow(row)"
                          >
                            删除
                          </el-button>
                        </template>
                      </el-table-column>
                      <template #empty>
                        <el-empty description="暂无换算数据，点击左下角「新增行」开始维护" :image-size="70" />
                      </template>
                    </el-table>

                    <div class="norm-detail-foot">
                      <el-button text size="small" class="op-btn op-primary" @click="addNormRow">
                        <el-icon class="op-icon"><Plus /></el-icon>新增行
                      </el-button>
                      <span class="norm-foot-tip">rawScore 需唯一，服务端按原始分 upsert；留空的换算值将存为 NULL</span>
                    </div>
                  </template>
                </div>
              </div>
            </el-tab-pane>

            <!-- 结果规则 -->
            <el-tab-pane name="result-rule">
              <template #label>
                <span class="tab-label"><el-icon><DataBoard /></el-icon>结果规则</span>
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
                <el-button :disabled="!selectedVersion || !resultRuleList.length" :loading="resultRuleSaving" @click="handleResultRuleBatchSave">
                  <el-icon class="op-icon"><Promotion /></el-icon>整体保存
                </el-button>
                <el-button type="primary" :disabled="!selectedVersion" @click="openAddResultRule(null)">
                  <el-icon class="btn-icon"><Plus /></el-icon>新增规则
                </el-button>
              </div>

              <div v-loading="resultRuleLoading" class="rule-wrap">
                <el-empty
                  v-if="!resultRuleList.length"
                  description="该版本暂无结果规则，请先新增「总分规则」或按维度维护"
                  :image-size="80"
                />
                <div v-else v-for="[dimensionId, rules] in groupedResultRules" :key="`${dimensionId ?? 'total'}`" class="rule-group-card">
                  <div class="rule-group-head">
                    <div class="rgh-left">
                      <span class="rgh-title">{{ resultRuleGroupTitle(dimensionId) }}</span>
                      <span class="rgh-count">{{ rules.length }} 条区间</span>
                    </div>
                    <div class="rgh-actions">
                      <el-button text size="small" class="op-btn op-edit" @click="openAddResultRule(dimensionId)">
                        <el-icon class="op-icon"><Plus /></el-icon>新增
                      </el-button>
                      <el-button text size="small" class="op-btn op-primary" :loading="resultRuleSaving" @click="handleResultRuleGroupSave(dimensionId)">
                        <el-icon class="op-icon"><Promotion /></el-icon>按维度保存
                      </el-button>
                    </div>
                  </div>
                  <el-table :data="rules" class="pane-table" row-key="id">
                    <el-table-column label="得分区间" min-width="150">
                      <template #default="{ row }">
                        <span class="score-range">{{ formatScore(row.minScore) }} ~ {{ formatScore(row.maxScore) }}</span>
                      </template>
                    </el-table-column>
                    <el-table-column label="结果文案" min-width="220" show-overflow-tooltip>
                      <template #default="{ row }">
                        <span class="cell-text">{{ row.resultText }}</span>
                      </template>
                    </el-table-column>
                    <el-table-column label="风险等级" width="110" align="center">
                      <template #default="{ row }">
                        <el-tag size="small" effect="light" :type="riskLevelTag(row.riskLevel).type as any">
                          {{ riskLevelTag(row.riskLevel).text }}
                        </el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column label="排序" width="80" align="center">
                      <template #default="{ row }">
                        <span class="num-text">{{ row.sort ?? 0 }}</span>
                      </template>
                    </el-table-column>
                    <el-table-column label="操作" width="140" align="center" fixed="right">
                      <template #default="{ row }">
                        <el-button text size="small" class="op-btn op-edit" @click="openEditResultRule(row)">编辑</el-button>
                        <el-button text size="small" class="op-btn op-del" @click="handleDeleteResultRule(row)">删除</el-button>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>
              </div>
            </el-tab-pane>

            <!-- 分支规则 -->
            <el-tab-pane name="branch-rule">
              <template #label>
                <span class="tab-label"><el-icon><Connection /></el-icon>分支规则</span>
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
                <el-button type="primary" :disabled="!selectedVersion" @click="openAddBranchRule">
                  <el-icon class="btn-icon"><Plus /></el-icon>新增规则
                </el-button>
              </div>

              <el-table v-loading="branchRuleLoading" :data="branchRuleList" class="pane-table" row-key="id">
                <el-table-column label="源题目" min-width="200" show-overflow-tooltip>
                  <template #default="{ row }">
                    <span class="q-title">{{ row.sourceQuestionTitle || "—" }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="源选项" width="110" align="center">
                  <template #default="{ row }">
                    <span v-if="row.sourceOptionText" class="branch-option">{{ row.sourceOptionText }}</span>
                    <span v-else class="muted">—</span>
                  </template>
                </el-table-column>
                <el-table-column label="跳转目标" min-width="200" show-overflow-tooltip>
                  <template #default="{ row }">
                    <span v-if="row.targetQuestionTitle" class="q-title">{{ row.targetQuestionTitle }}</span>
                    <span v-else class="branch-end">结束测评</span>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="150" align="center" fixed="right">
                  <template #default="{ row }">
                    <el-button text size="small" class="op-btn op-edit" @click="openEditBranchRule(row)">编辑</el-button>
                    <el-button text size="small" class="op-btn op-del" @click="handleDeleteBranchRule(row)">删除</el-button>
                  </template>
                </el-table-column>
                <template #empty>
                  <el-empty description="该版本暂无跳题规则" :image-size="70" />
                </template>
              </el-table>
            </el-tab-pane>

            <!-- 选项模板 -->
            <el-tab-pane name="optionTemplate">
              <template #label>
                <span class="tab-label"><el-icon><Menu /></el-icon>选项模板</span>
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
                <el-button type="primary" :disabled="!selectedVersion" @click="openAddOptionTemplate">
                  <el-icon class="btn-icon"><Plus /></el-icon>新增模板
                </el-button>
              </div>

              <div v-loading="optionTemplateLoading" class="otm-wrap">
                <el-empty
                  v-if="!optionTemplateList.length"
                  description="该版本暂无选项模板，请先新增"
                  :image-size="80"
                />
                <div v-else class="otm-cards">
                  <div v-for="t in optionTemplateList" :key="t.id" class="otm-card">
                    <div class="otm-card-head">
                      <div class="otm-card-title">
                        <span class="otm-name">{{ t.templateName }}</span>
                        <span class="otm-count">{{ (t.items ?? []).length }} 项</span>
                      </div>
                      <div class="otm-card-ops">
                        <el-button text size="small" class="op-btn op-edit" @click="openEditOptionTemplate(t)">编辑</el-button>
                        <el-button text size="small" class="op-btn op-primary" @click="openApplyOptionTemplate(t)">
                          <el-icon class="op-icon"><Promotion /></el-icon>应用
                        </el-button>
                        <el-button text size="small" class="op-btn op-primary" @click="openCopyOptionTemplate(t)">
                          <el-icon class="op-icon"><CopyDocument /></el-icon>复制
                        </el-button>
                        <el-button text size="small" class="op-btn op-del" @click="handleDeleteOptionTemplate(t)">删除</el-button>
                      </div>
                    </div>
                    <div v-if="t.templateDesc" class="otm-desc">{{ t.templateDesc }}</div>
                    <div class="otm-items">
                      <span v-for="(it, i) in (t.items ?? [])" :key="i" class="otm-item">
                        {{ it.optionText }}({{ it.score ?? 0 }})
                      </span>
                      <span v-if="!(t.items ?? []).length" class="muted">—</span>
                    </div>
                    <div class="otm-foot">
                      <span class="time-text">创建于 {{ formatTime(t.createdTime) }}</span>
                    </div>
                  </div>
                </div>
              </div>
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

    <!-- ==================== 新增 / 编辑常模组弹窗 ==================== -->
    <el-dialog
      v-model="normGroupDialogVisible"
      :title="normGroupForm.id == null ? '新增常模组' : '编辑常模组'"
      width="620px"
      align-center
      :close-on-click-modal="false"
    >
      <el-form ref="normGroupFormRef" :model="normGroupForm" :rules="normGroupRules" label-width="100px">
        <el-form-item label="常模组名称" prop="groupName">
          <el-input v-model="normGroupForm.groupName" placeholder="如 全国成年男性常模" maxlength="60" show-word-limit clearable />
        </el-form-item>
        <el-form-item label="常模组编码">
          <el-input v-model="normGroupForm.groupCode" placeholder="程序查找用，如 ADULT_MALE" maxlength="40" clearable />
          <div class="form-tip">留空则自动生成</div>
        </el-form-item>
        <el-form-item label="适用维度">
          <el-select v-model="normGroupForm.dimensionId" placeholder="留空表示总分常模" clearable style="width: 100%">
            <el-option v-for="d in dimensionList" :key="d.id" :label="d.dimName" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-divider content-position="left">适用人群</el-divider>
        <div class="norm-pop-row">
          <el-form-item label="性别" class="norm-pop-item">
            <el-select v-model="normGroupForm.gender" placeholder="不限" clearable style="width: 100%">
              <el-option v-for="o in GENDER_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="年龄范围" class="norm-pop-item">
            <div class="age-range">
              <el-input-number v-model="normGroupForm.ageMin" :min="0" :max="150" :controls="false" placeholder="最小" style="width: 82px" />
              <span class="age-sep">~</span>
              <el-input-number v-model="normGroupForm.ageMax" :min="0" :max="150" :controls="false" placeholder="最大" style="width: 82px" />
            </div>
          </el-form-item>
        </div>
        <div class="norm-pop-row">
          <el-form-item label="学历" class="norm-pop-item">
            <el-select v-model="normGroupForm.education" placeholder="不限" clearable style="width: 100%">
              <el-option v-for="o in EDUCATION_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="职业群体" class="norm-pop-item">
            <el-select v-model="normGroupForm.occupation" placeholder="不限" clearable style="width: 100%">
              <el-option v-for="o in OCCUPATION_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="地区">
          <el-select v-model="normGroupForm.region" placeholder="不限" clearable style="width: 100%">
            <el-option v-for="o in REGION_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-divider content-position="left">换算参数</el-divider>
        <el-form-item label="计算方式" prop="normType">
          <el-radio-group v-model="normGroupForm.normType">
            <el-radio-button v-for="t in NORM_TYPE_OPTIONS" :key="t.value" :value="t.value">{{ t.label }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <template v-if="normGroupForm.normType === 0">
          <div class="norm-pop-row">
            <el-form-item label="原始分均值 M" class="norm-pop-item">
              <el-input-number v-model="normGroupForm.mean" :precision="2" :step="0.5" :controls="false" style="width: 100%" placeholder="如 32.5" />
            </el-form-item>
            <el-form-item label="标准差 SD" class="norm-pop-item">
              <el-input-number v-model="normGroupForm.sd" :precision="2" :step="0.1" :min="0" :controls="false" style="width: 100%" placeholder="如 6.8" />
            </el-form-item>
          </div>
          <div class="form-tip" style="margin: -4px 0 6px">公式法：T = 50 + 10 × (X - M) / SD</div>
        </template>
        <el-form-item v-else label="说明">
          <div class="form-tip">查表法需在「常模换算表」中维护 原始分 → 各标准分 的对照数据</div>
        </el-form-item>
        <div class="norm-pop-row">
          <el-form-item label="常模年份" class="norm-pop-item">
            <el-input-number v-model="normGroupForm.normYear" :min="1950" :max="2100" :controls="false" style="width: 100%" placeholder="如 2020" />
          </el-form-item>
          <el-form-item label="排序" class="norm-pop-item">
            <el-input-number v-model="normGroupForm.sort" :min="0" :max="9999" style="width: 100%" />
          </el-form-item>
        </div>
        <el-form-item label="常模来源">
          <el-input v-model="normGroupForm.source" placeholder="如 XX量表使用手册（2020）" maxlength="200" clearable />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="normGroupDialogVisible = false">取 消</el-button>
        <el-button type="primary" :loading="normGroupSubmitting" @click="handleNormGroupSubmit">确 定</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 新增 / 编辑结果规则弹窗 ==================== -->
    <el-dialog
      v-model="resultRuleDialogVisible"
      :title="resultRuleForm.id == null ? '新增结果规则' : '编辑结果规则'"
      width="560px"
      align-center
      :close-on-click-modal="false"
    >
      <el-form ref="resultRuleFormRef" :model="resultRuleForm" :rules="resultRuleRules" label-width="100px">
        <el-form-item label="适用范围">
          <el-select v-model="resultRuleForm.dimensionId" placeholder="留空表示总分规则" clearable style="width: 100%">
            <el-option v-for="d in dimensionList" :key="d.id" :label="d.dimName" :value="d.id" />
          </el-select>
          <div class="form-tip">同一维度下各区间不可重叠</div>
        </el-form-item>
        <div class="norm-pop-row">
          <el-form-item label="最低分" prop="minScore" class="norm-pop-item">
            <el-input-number v-model="resultRuleForm.minScore" :precision="2" :controls="false" style="width: 100%" placeholder="区间最低分（含）" />
          </el-form-item>
          <el-form-item label="最高分" prop="maxScore" class="norm-pop-item">
            <el-input-number v-model="resultRuleForm.maxScore" :precision="2" :controls="false" style="width: 100%" placeholder="区间最高分（含）" />
          </el-form-item>
        </div>
        <el-form-item label="结果文案" prop="resultText">
          <el-input v-model="resultRuleForm.resultText" type="textarea" :rows="3" placeholder="如 轻度抑郁状态，建议关注情绪变化" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="风险等级">
          <el-select v-model="resultRuleForm.riskLevel" style="width: 200px">
            <el-option v-for="o in RISK_LEVEL_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <div class="form-tip">0=正常（绿） 1=轻度 2=中度 3=重度（预警）</div>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="resultRuleForm.sort" :min="0" :max="9999" style="width: 160px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resultRuleDialogVisible = false">取 消</el-button>
        <el-button type="primary" :loading="resultRuleSubmitting" @click="handleResultRuleSubmit">确 定</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 新增 / 编辑跳题规则弹窗 ==================== -->
    <el-dialog
      v-model="branchRuleDialogVisible"
      :title="branchRuleForm.id == null ? '新增跳题规则' : '编辑跳题规则'"
      width="580px"
      align-center
      :close-on-click-modal="false"
    >
      <el-form ref="branchRuleFormRef" :model="branchRuleForm" :rules="branchRuleRules" label-width="90px">
        <el-form-item label="源题目" prop="sourceQuestionId">
          <el-select
            v-model="branchRuleForm.sourceQuestionId"
            placeholder="请选择触发题目"
            filterable
            style="width: 100%"
            @change="handleBranchSourceChange"
          >
            <el-option v-for="q in questionList" :key="q.id" :label="q.title" :value="q.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="源选项" prop="sourceOptionId">
          <el-select v-model="branchRuleForm.sourceOptionId" placeholder="请选择触发选项" style="width: 100%">
            <el-option v-for="o in branchSourceOptions" :key="o.id" :label="o.optionText ?? `选项 ${o.id}`" :value="o.id as number" />
          </el-select>
          <div class="form-tip">选中该选项后触发跳转</div>
        </el-form-item>
        <el-form-item label="跳转目标">
          <el-select v-model="branchRuleForm.targetQuestionId" placeholder="留空表示结束测评" clearable filterable style="width: 100%">
            <el-option v-for="q in questionList" :key="q.id" :label="q.title" :value="q.id" />
          </el-select>
          <div class="form-tip">留空表示命中该规则后结束测评</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="branchRuleDialogVisible = false">取 消</el-button>
        <el-button type="primary" :loading="branchRuleSubmitting" @click="handleBranchRuleSubmit">确 定</el-button>
      </template>
    </el-dialog>

      <!-- ==================== 新增 / 编辑选项模板弹窗 ==================== -->
      <el-dialog
        v-model="optionTemplateDialogVisible"
        :title="optionTemplateForm.id == null ? '新增选项模板' : '编辑选项模板'"
        width="620px"
        align-center
        :close-on-click-modal="false"
      >
        <el-form ref="optionTemplateFormRef" :model="optionTemplateForm" :rules="optionTemplateRules" label-width="90px">
          <el-form-item label="模板名称" prop="templateName">
            <el-input v-model="optionTemplateForm.templateName" placeholder="如 五点李克特通用选项" maxlength="60" show-word-limit clearable />
          </el-form-item>
          <el-form-item label="模板描述">
            <el-input v-model="optionTemplateForm.templateDesc" type="textarea" :rows="2" placeholder="选填" maxlength="200" show-word-limit />
          </el-form-item>
          <el-form-item label="选项项">
            <div class="option-editor">
              <div class="option-editor-head">
                <span class="option-editor-title">选项配置</span>
                <span class="option-editor-tip">为每个选项设置文本、原始分值（score）与排序（sort）</span>
                <div class="toolbar-spacer" />
                <el-button size="small" type="primary" plain @click="addTemplateOptionRow">
                  <el-icon class="btn-icon"><Plus /></el-icon>添加选项
                </el-button>
              </div>
              <div v-if="templateOptionRows.length" class="option-rows">
                <div v-for="(opt, i) in templateOptionRows" :key="i" class="option-row">
                  <span class="option-idx">{{ i + 1 }}</span>
                  <el-input v-model="opt.optionText" placeholder="选项文本" maxlength="50" clearable />
                  <el-input-number v-model="opt.score" :min="0" placeholder="分值" style="width: 130px" />
                  <el-input-number v-model="opt.sort" :min="0" :max="9999" placeholder="排序" style="width: 130px" />
                  <el-button text class="op-btn op-del" @click="removeTemplateOptionRow(i)">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </div>
              </div>
              <div v-else class="option-empty">暂无选项，点击「添加选项」开始配置</div>
            </div>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="optionTemplateDialogVisible = false">取 消</el-button>
          <el-button type="primary" :loading="optionTemplateSubmitting" @click="handleOptionTemplateSubmit">确 定</el-button>
        </template>
      </el-dialog>

      <!-- ==================== 应用选项模板弹窗 ==================== -->
      <el-dialog v-model="optionTemplateApplyVisible" title="应用选项模板" width="560px" align-center :close-on-click-modal="false">
        <el-alert
          v-if="optionTemplateApplySource"
          :title="`将模板「${optionTemplateApplySource.templateName}」应用到所选题目`"
          type="info"
          :closable="false"
          class="copy-alert"
        />
        <el-form label-width="90px">
          <el-form-item label="应用方式">
            <el-radio-group v-model="optionTemplateApplyMode">
              <el-radio-button :value="'REPLACE'">覆盖（REPLACE）</el-radio-button>
              <el-radio-button :value="'APPEND'">追加（APPEND）</el-radio-button>
            </el-radio-group>
            <div class="form-tip">覆盖：用模板选项替换题目全部原有选项；追加：在题目原有选项之后追加模板选项</div>
          </el-form-item>
          <el-form-item label="目标题目">
            <el-select
              v-model="optionTemplateApplyQuestionIds"
              multiple
              filterable
              placeholder="请选择题目（可多选）"
              style="width: 100%"
            >
              <el-option v-for="q in questionList" :key="q.id" :label="q.title" :value="q.id" />
            </el-select>
            <div class="form-tip">当前版本共 {{ questionList.length }} 道题目</div>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="optionTemplateApplyVisible = false">取 消</el-button>
          <el-button type="primary" :loading="optionTemplateApplySubmitting" @click="handleOptionTemplateApply">确 定</el-button>
        </template>
      </el-dialog>

      <!-- ==================== 复制选项模板弹窗 ==================== -->
      <el-dialog v-model="optionTemplateCopyVisible" title="复制选项模板" width="440px" align-center :close-on-click-modal="false">
        <el-alert
          v-if="optionTemplateCopySource"
          :title="`将选项模板「${optionTemplateCopySource.templateName}」复制到目标版本`"
          type="info"
          :closable="false"
          class="copy-alert"
        />
        <el-form label-width="90px">
          <el-form-item label="目标版本">
            <el-select v-model="optionTemplateCopyVersionId" placeholder="请选择目标版本" style="width: 100%">
              <el-option v-for="v in copyableVersions" :key="v.id" :label="`${v.versionNo}${v.isCurrent ? '（当前）' : ''}`" :value="v.id" />
            </el-select>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="optionTemplateCopyVisible = false">取 消</el-button>
          <el-button type="primary" :loading="optionTemplateCopySubmitting" @click="handleOptionTemplateCopy">确 定</el-button>
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
  Odometer,
  DataBoard,
  Connection,
  Menu,
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
import {
  getScaleNormGroupList,
  addScaleNormGroup,
  updateScaleNormGroup,
  deleteScaleNormGroup,
  getScaleNormList,
  saveScaleNormBatch,
  deleteScaleNorms,
} from "@/admin/api/scale/norm";
import {
  getScaleResultRuleList,
  addScaleResultRule,
  updateScaleResultRule,
  deleteScaleResultRule,
  saveScaleResultRuleBatch,
} from "@/admin/api/scale/result-rule";
import {
  getScaleBranchRuleList,
  addScaleBranchRule,
  updateScaleBranchRule,
  deleteScaleBranchRule,
} from "@/admin/api/scale/branch-rule";
import {
  getOptionTemplateList,
  addOptionTemplate,
  updateOptionTemplate,
  applyOptionTemplate,
  copyOptionTemplate,
  deleteOptionTemplate,
} from "@/admin/api/scale/option-template";
import type {
  AdminScaleOptionTemplateVO,
  AdminScaleOptionTemplateItemVO,
  AdminScaleOptionTemplateItemDTO,
  AdminScaleOptionTemplateDTO,
} from "@/admin/api/scale/option-template";
import { getScaleCategoryPage } from "@/admin/api/scale/category";
import type {
  AdminScaleDetailVO,
  AdminScaleDimensionVO,
  AdminScaleQuestionVO,
  AdminScaleVersionVO,
  AdminScaleCategoryVO,
  AdminScaleOptionItemDTO,
  AdminScaleNormGroupVO,
  AdminScaleResultRuleVO,
  AdminScaleBranchRuleVO,
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

/* ==================== 常模管理 ==================== */
const NORM_TYPE_OPTIONS = [
  { label: "公式法（M/SD）", value: 0 },
  { label: "查表法（换算表）", value: 1 },
];
const GENDER_OPTIONS = [
  { label: "男", value: 1 },
  { label: "女", value: 0 },
];
const LEVEL_LABEL_OPTIONS = [
  { label: "极低", value: 0 },
  { label: "偏低", value: 1 },
  { label: "正常", value: 2 },
  { label: "偏高", value: 3 },
  { label: "极高", value: 4 },
];

const normGroupList = ref<AdminScaleNormGroupVO[]>([]);
const normGroupLoading = ref(false);
const normGroupDialogVisible = ref(false);
const normGroupSubmitting = ref(false);
const normGroupFormRef = ref<FormInstance>();
const normGroupForm = reactive({
  id: null as number | null,
  groupName: "",
  groupCode: "",
  dimensionId: null as number | null,
  gender: null as number | null,
  ageMin: null as number | null,
  ageMax: null as number | null,
  education: null as number | null,
  occupation: null as number | null,
  region: null as number | null,
  normType: 0,
  mean: null as number | null,
  sd: null as number | null,
  source: "",
  normYear: null as number | null,
  sort: 0,
});
const normGroupRules: FormRules = {
  groupName: [{ required: true, message: "请输入常模组名称", trigger: "blur" }],
};

const selectedNormGroupId = ref<number | null>(null);
const normLoading = ref(false);
const normSaving = ref(false);

const activeNormGroup = computed(() => normGroupList.value.find((g) => g.id === selectedNormGroupId.value) ?? null);

/** 常模换算表编辑行 */
interface AdminScaleNormRowState {
  id: number | null;
  rawScore: number | null;
  tScore: number | null;
  zScore: number | null;
  percentile: number | null;
  stanine: number | null;
  diq: number | null;
  levelLabel: number | null;
}

const normRows = ref<AdminScaleNormRowState[]>([]);

/* ==================== 结果规则 ==================== */
const RISK_LEVEL_OPTIONS = [
  { label: "正常", value: 0, type: "success" },
  { label: "轻度", value: 1, type: "primary" },
  { label: "中度", value: 2, type: "warning" },
  { label: "重度", value: 3, type: "danger" },
];

const EDUCATION_OPTIONS = [
  { label: "初中", value: 1 },
  { label: "高中", value: 2 },
  { label: "大专", value: 3 },
  { label: "本科", value: 4 },
  { label: "硕士", value: 5 },
  { label: "博士", value: 6 },
];

const OCCUPATION_OPTIONS = [
  { label: "学生", value: 1 },
  { label: "医护", value: 2 },
  { label: "军人", value: 3 },
  { label: "企业员工", value: 4 },
];

const REGION_OPTIONS = [
  { label: "华北", value: 1 },
  { label: "华东", value: 2 },
  { label: "华南", value: 3 },
  { label: "华中", value: 4 },
  { label: "西南", value: 5 },
];

const resultRuleList = ref<AdminScaleResultRuleVO[]>([]);
const resultRuleLoading = ref(false);
const resultRuleDialogVisible = ref(false);
const resultRuleSubmitting = ref(false);
const resultRuleFormRef = ref<FormInstance>();
const resultRuleForm = reactive({
  id: null as number | null,
  dimensionId: null as number | null,
  minScore: null as number | null,
  maxScore: null as number | null,
  resultText: "",
  riskLevel: 0,
  sort: 0,
});
const resultRuleRules: FormRules = {
  minScore: [{ required: true, message: "请输入最低分", trigger: "blur" }],
  maxScore: [{ required: true, message: "请输入最高分", trigger: "blur" }],
  resultText: [{ required: true, message: "请输入结果文案", trigger: "blur" }],
};
const resultRuleSaving = ref(false);

const groupedResultRules = computed(() => {
  const map = new Map<number | null, AdminScaleResultRuleVO[]>();
  for (const rule of resultRuleList.value) {
    const key = rule.dimensionId ?? null;
    const list = map.get(key) ?? [];
    list.push(rule);
    map.set(key, list);
  }
  return [...map.entries()].sort((a, b) => {
    if (a[0] === null) return -1;
    if (b[0] === null) return 1;
    return a[0] - b[0];
  });
});

function resultRuleGroupTitle(dimensionId: number | null): string {
  if (dimensionId === null) return "总分规则";
  const dim = dimensionList.value.find((d) => d.id === dimensionId);
  return dim?.dimName ?? `维度 ${dimensionId}`;
}

/** 整体保存：将当前版本的整份规则集提交，服务端差异更新（增/改/删） */
async function handleResultRuleBatchSave() {
  const vid = selectedVersionId.value;
  if (vid == null) {
    ElMessage.warning("请先选择量表版本");
    return;
  }
  if (!resultRuleList.value.length) {
    ElMessage.warning("暂无结果规则可保存");
    return;
  }
  resultRuleSaving.value = true;
  try {
    await saveScaleResultRuleBatch({
      scaleVersionId: vid,
      rules: resultRuleList.value.map((r) => ({
        id: r.id,
        scaleVersionId: vid,
        dimensionId: r.dimensionId ?? undefined,
        minScore: Number(r.minScore),
        maxScore: Number(r.maxScore),
        resultText: r.resultText,
        riskLevel: r.riskLevel ?? 0,
        sort: r.sort ?? 0,
      })),
    });
    ElMessage.success("结果规则已整体保存");
    await loadResultRules(vid);
  } catch (e) {
    ElMessage.error((e as Error)?.message || "保存失败，请稍后重试");
  } finally {
    resultRuleSaving.value = false;
  }
}

/* ==================== 分支规则 ==================== */
const branchRuleList = ref<AdminScaleBranchRuleVO[]>([]);
const branchRuleLoading = ref(false);
const branchRuleDialogVisible = ref(false);
const branchRuleSubmitting = ref(false);
const branchRuleFormRef = ref<FormInstance>();
const branchRuleForm = reactive({
  id: null as number | null,
  sourceQuestionId: null as number | null,
  sourceOptionId: null as number | null,
  targetQuestionId: null as number | null,
});
const branchRuleRules: FormRules = {
  sourceQuestionId: [{ required: true, message: "请选择源题目", trigger: "change" }],
  sourceOptionId: [{ required: true, message: "请选择源选项", trigger: "change" }],
};

const branchSourceOptions = computed(() => {
  const q = questionList.value.find((item) => item.id === branchRuleForm.sourceQuestionId);
  return (q?.options ?? []).filter((o) => o.id != null);
});

/* ==================== 选项模板 ==================== */
const optionTemplateList = ref<AdminScaleOptionTemplateVO[]>([]);
const optionTemplateLoading = ref(false);

const optionTemplateDialogVisible = ref(false);
const optionTemplateSubmitting = ref(false);
const optionTemplateFormRef = ref<FormInstance>();
const optionTemplateForm = reactive({
  id: null as number | null,
  templateName: "",
  templateDesc: "",
});
const optionTemplateRules: FormRules = {
  templateName: [{ required: true, message: "请输入模板名称", trigger: "blur" }],
};
const templateOptionRows = ref<AdminScaleOptionTemplateItemDTO[]>([]);

const optionTemplateApplyVisible = ref(false);
const optionTemplateApplySubmitting = ref(false);
const optionTemplateApplySource = ref<AdminScaleOptionTemplateVO | null>(null);
const optionTemplateApplyQuestionIds = ref<number[]>([]);
const optionTemplateApplyMode = ref<string>("REPLACE");

const optionTemplateCopyVisible = ref(false);
const optionTemplateCopySubmitting = ref(false);
const optionTemplateCopyVersionId = ref<number | null>(null);
const optionTemplateCopySource = ref<AdminScaleOptionTemplateVO | null>(null);

/** 复制目标版本（排除当前版本） */
const copyableVersions = computed(() =>
  versions.value.filter((v) => v.id !== selectedVersionId.value),
);

function addTemplateOptionRow() {
  templateOptionRows.value.push({ optionText: "", score: 0, sort: templateOptionRows.value.length });
}

function removeTemplateOptionRow(index: number) {
  templateOptionRows.value.splice(index, 1);
}

function buildTemplateItemPayload(): AdminScaleOptionTemplateItemDTO[] {
  return templateOptionRows.value
    .filter((r) => r.optionText.trim())
    .map((r, i) => ({ optionText: r.optionText.trim(), score: r.score ?? 0, sort: r.sort ?? i }));
}

const categoryList = ref<AdminScaleCategoryVO[]>([]);

/* ==================== 工具函数 ==================== */
function formatTime(v?: string | number): string {
  if (v === undefined || v === null || v === "") return "—";
  // 后端可能返回时间戳（number）或 ISO 字符串；number 转 Date，字符串沿用原格式
  if (typeof v === "number") {
    const d = new Date(v);
    const pad = (n: number) => String(n).padStart(2, "0");
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }
  return String(v).replace("T", " ").slice(0, 16);
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

async function loadNormGroups(versionId: number) {
  normGroupLoading.value = true;
  try {
    const res = await getScaleNormGroupList(versionId);
    normGroupList.value = res.data.data ?? [];
    if (selectedNormGroupId.value != null && !normGroupList.value.some((g) => g.id === selectedNormGroupId.value)) {
      selectedNormGroupId.value = null;
      normRows.value = [];
    }
  } catch (e) {
    normGroupList.value = [];
    normRows.value = [];
    ElMessage.error((e as Error)?.message || "常模组列表加载失败");
  } finally {
    normGroupLoading.value = false;
  }
}

async function loadNorms(normGroupId: number) {
  normLoading.value = true;
  try {
    const res = await getScaleNormList(normGroupId);
    normRows.value = [...(res.data.data ?? [])].sort((a, b) => Number(a.rawScore) - Number(b.rawScore)).map((n) => ({
      id: n.id,
      rawScore: typeof n.rawScore === "number" ? n.rawScore : Number(n.rawScore),
      tScore: n.tScore ?? null,
      zScore: n.zScore ?? null,
      percentile: n.percentile ?? null,
      stanine: n.stanine ?? null,
      diq: n.diq ?? null,
      levelLabel: n.levelLabel ?? null,
    }));
  } catch (e) {
    normRows.value = [];
    ElMessage.error((e as Error)?.message || "常模明细加载失败");
  } finally {
    normLoading.value = false;
  }
}

async function loadResultRules(versionId: number) {
  resultRuleLoading.value = true;
  try {
    const res = await getScaleResultRuleList(versionId);
    resultRuleList.value = res.data.data ?? [];
  } catch (e) {
    resultRuleList.value = [];
    ElMessage.error((e as Error)?.message || "结果规则加载失败");
  } finally {
    resultRuleLoading.value = false;
  }
}

async function loadBranchRules(versionId: number) {
  branchRuleLoading.value = true;
  try {
    const res = await getScaleBranchRuleList(versionId);
    branchRuleList.value = res.data.data ?? [];
  } catch (e) {
    branchRuleList.value = [];
    ElMessage.error((e as Error)?.message || "跳题规则加载失败");
  } finally {
    branchRuleLoading.value = false;
  }
}

async function loadOptionTemplates(versionId: number) {
  optionTemplateLoading.value = true;
  try {
    const res = await getOptionTemplateList(versionId);
    optionTemplateList.value = res.data.data ?? [];
  } catch (e) {
    optionTemplateList.value = [];
    ElMessage.error((e as Error)?.message || "选项模板加载失败");
  } finally {
    optionTemplateLoading.value = false;
  }
}

watch(selectedVersionId, (id) => {
  if (id == null) {
    dimensionList.value = [];
    questionList.value = [];
    normGroupList.value = [];
    normRows.value = [];
    selectedNormGroupId.value = null;
    resultRuleList.value = [];
    branchRuleList.value = [];
    optionTemplateList.value = [];
    return;
  }
  loadDimensions(id);
  loadQuestions(id);
  loadNormGroups(id);
  loadResultRules(id);
  loadBranchRules(id);
  loadOptionTemplates(id);
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

/* ==================== 交互：常模管理 ==================== */
function maxNormSort(): number {
  return normGroupList.value.reduce((m, g) => Math.max(m, g.sort ?? 0), -1);
}

function openAddNormGroup() {
  Object.assign(normGroupForm, {
    id: null,
    groupName: "",
    groupCode: "",
    dimensionId: null,
    gender: null,
    ageMin: null,
    ageMax: null,
    education: null,
    occupation: null,
    region: null,
    normType: 0,
    mean: null,
    sd: null,
    source: "",
    normYear: null,
    sort: maxNormSort() + 1,
  });
  normGroupDialogVisible.value = true;
}

function openEditNormGroup(row: any) {
  Object.assign(normGroupForm, {
    id: row.id,
    groupName: row.groupName ?? "",
    groupCode: row.groupCode ?? "",
    dimensionId: row.dimensionId ?? null,
    gender: row.gender ?? null,
    ageMin: row.ageMin ?? null,
    ageMax: row.ageMax ?? null,
    education: row.education ?? null,
    occupation: row.occupation ?? null,
    region: row.region ?? null,
    normType: row.normType ?? 0,
    mean: row.mean ?? null,
    sd: row.sd ?? null,
    source: row.source ?? "",
    normYear: row.normYear ?? null,
    sort: row.sort ?? 0,
  });
  normGroupDialogVisible.value = true;
}

async function handleNormGroupSubmit() {
  const vid = selectedVersionId.value;
  if (vid == null) {
    ElMessage.warning("请先选择量表版本");
    return;
  }
  const valid = await normGroupFormRef.value?.validate().catch(() => false);
  if (!valid) return;

  const requireMinMax = normGroupForm.normType === 0;
  if (requireMinMax && (normGroupForm.mean == null || normGroupForm.sd == null || Number(normGroupForm.sd) <= 0)) {
    ElMessage.warning("公式法需要提供原始分均值（M）与标准差（SD），且 SD 必须大于 0");
    return;
  }
  if (normGroupForm.ageMin != null && normGroupForm.ageMax != null && normGroupForm.ageMin > normGroupForm.ageMax) {
    ElMessage.warning("最小年龄不能大于最大年龄");
    return;
  }

  normGroupSubmitting.value = true;
  try {
    const payload = {
      scaleVersionId: vid,
      dimensionId: normGroupForm.dimensionId,
      groupName: normGroupForm.groupName.trim(),
      groupCode: normGroupForm.groupCode.trim() || undefined,
      gender: normGroupForm.gender,
      ageMin: normGroupForm.ageMin,
      ageMax: normGroupForm.ageMax,
      education: normGroupForm.education,
      occupation: normGroupForm.occupation,
      region: normGroupForm.region,
      normType: normGroupForm.normType,
      mean: normGroupForm.mean,
      sd: normGroupForm.sd,
      source: normGroupForm.source.trim() || undefined,
      normYear: normGroupForm.normYear,
      sort: normGroupForm.sort,
    };
    if (normGroupForm.id == null) {
      await addScaleNormGroup(payload);
      ElMessage.success("常模组已新增");
    } else {
      await updateScaleNormGroup(normGroupForm.id, payload);
      ElMessage.success("常模组已更新");
    }
    normGroupDialogVisible.value = false;
    await loadNormGroups(vid);
  } catch (e) {
    ElMessage.error((e as Error)?.message || "保存失败，请稍后重试");
  } finally {
    normGroupSubmitting.value = false;
  }
}

async function handleNormGroupSelect(group: AdminScaleNormGroupVO) {
  selectedNormGroupId.value = group.id;
  await loadNorms(group.id);
}

function handleNormGroupRow(row: any) {
  if (row.id !== selectedNormGroupId.value) {
    handleNormGroupSelect(row);
  }
}

async function handleDeleteNormGroup(row: any) {
  try {
    await ElMessageBox.confirm(
      `确定删除常模组「${row.groupName}」吗？其常模明细将一并删除；已被测评记录引用的常模组将禁止删除。`,
      "删除确认",
      { type: "warning", confirmButtonText: "删除", cancelButtonText: "取消", confirmButtonClass: "el-button--danger" },
    );
  } catch {
    return;
  }
  try {
    await deleteScaleNormGroup(row.id);
    ElMessage.success("常模组已删除");
    if (selectedNormGroupId.value === row.id) {
      selectedNormGroupId.value = null;
      normRows.value = [];
    }
    const vid = selectedVersionId.value;
    if (vid != null) await loadNormGroups(vid);
  } catch (e) {
    ElMessage.error((e as Error)?.message || "删除失败，请稍后重试");
  }
}

function addNormRow() {
  normRows.value.push({
    id: null,
    rawScore: null,
    tScore: null,
    zScore: null,
    percentile: null,
    stanine: null,
    diq: null,
    levelLabel: null,
  });
}

function removeNormRow(index: number) {
  normRows.value.splice(index, 1);
}

async function handleNormRowsSave() {
  const group = activeNormGroup.value;
  if (!group) {
    ElMessage.warning("请先选择常模组");
    return;
  }
  const rows = normRows.value.filter((r) => r.rawScore != null);
  if (rows.length === 0) {
    ElMessage.warning("请至少维护一条「原始分」记录");
    return;
  }
  const rawScores = rows.map((r) => Number(r.rawScore));
  if (new Set(rawScores).size !== rawScores.length) {
    ElMessage.warning("原始分存在重复，同一常模组下原始分需唯一");
    return;
  }
  normSaving.value = true;
  try {
    await saveScaleNormBatch({
      normGroupId: group.id,
      items: rows.map((r) => ({
        id: r.id ?? undefined,
        normGroupId: group.id,
        dimensionId: group.dimensionId ?? undefined,
        rawScore: Number(r.rawScore),
        tScore: r.tScore ?? undefined,
        zScore: r.zScore ?? undefined,
        percentile: r.percentile ?? undefined,
        stanine: r.stanine ?? undefined,
        diq: r.diq ?? undefined,
        levelLabel: r.levelLabel ?? undefined,
      })),
    });
    ElMessage.success("常模换算表已保存");

    const vid = selectedVersionId.value;
    const targetVid = vid ?? group.scaleVersionId;
    await loadNormGroups(targetVid ?? group.id);
  } catch (e) {
    ElMessage.error((e as Error)?.message || "保存失败，请稍后重试");
  } finally {
    normSaving.value = false;
  }
}

async function handleDeleteNormRow(row: any) {
  const group = activeNormGroup.value;
  if (!group || row.id == null) return;
  try {
    await ElMessageBox.confirm("确定删除该条常模换算记录吗？", "删除确认", {
      type: "warning",
      confirmButtonText: "删除",
      cancelButtonText: "取消",
      confirmButtonClass: "el-button--danger",
    });
  } catch {
    return;
  }
  try {
    await deleteScaleNorms({ normGroupId: group.id, normIds: [row.id] });
    ElMessage.success("已删除");
    await loadNorms(group.id);
  } catch (e) {
    ElMessage.error((e as Error)?.message || "删除失败，请稍后重试");
  }
}

function normGroupGenderText(g?: number | null): string {
  return g == null ? "不限" : g === 1 ? "男" : "女";
}

function educationText(v?: number | null): string {
  return v == null ? "不限" : (EDUCATION_OPTIONS.find((o) => o.value === v)?.label ?? "不限");
}

function occupationText(v?: number | null): string {
  return v == null ? "不限" : (OCCUPATION_OPTIONS.find((o) => o.value === v)?.label ?? "不限");
}

function regionText(v?: number | null): string {
  return v == null ? "不限" : (REGION_OPTIONS.find((o) => o.value === v)?.label ?? "不限");
}

function normGroupAppliedInfo(g: AdminScaleNormGroupVO): string {
  return `${normGroupGenderText(g.gender)}，${g.ageMin ?? "—"}~${g.ageMax ?? "—"} 岁，${educationText(g.education)}，${occupationText(g.occupation)}，${regionText(g.region)}`;
}

/** 数值展示（空值显示 —，小数保留 2 位） */
function fmtNum(v?: number | null): string {
  return v == null ? "—" : String(Number.isInteger(v) ? v : Number(v).toFixed(2));
}

/* ==================== 交互：结果规则 ==================== */
function openAddResultRule(dimensionId?: number | null) {
  Object.assign(resultRuleForm, {
    id: null,
    dimensionId: dimensionId ?? null,
    minScore: null,
    maxScore: null,
    resultText: "",
    riskLevel: 0,
    sort: resultRuleList.value.filter((r) => (r.dimensionId ?? null) === (dimensionId ?? null)).length,
  });
  resultRuleDialogVisible.value = true;
}

function openEditResultRule(row: any) {
  Object.assign(resultRuleForm, {
    id: row.id,
    dimensionId: row.dimensionId ?? null,
    minScore: row.minScore,
    maxScore: row.maxScore,
    resultText: row.resultText ?? "",
    riskLevel: row.riskLevel ?? 0,
    sort: row.sort ?? 0,
  });
  resultRuleDialogVisible.value = true;
}

async function handleResultRuleSubmit() {
  const vid = selectedVersionId.value;
  if (vid == null) {
    ElMessage.warning("请先选择量表版本");
    return;
  }
  const valid = await resultRuleFormRef.value?.validate().catch(() => false);
  if (!valid) return;
  if (resultRuleForm.minScore != null && resultRuleForm.maxScore != null && resultRuleForm.minScore > resultRuleForm.maxScore) {
    ElMessage.warning("最低分不能大于最高分");
    return;
  }
  resultRuleSubmitting.value = true;
  try {
    const payload = {
      scaleVersionId: vid,
      dimensionId: resultRuleForm.dimensionId,
      minScore: Number(resultRuleForm.minScore),
      maxScore: Number(resultRuleForm.maxScore),
      resultText: resultRuleForm.resultText.trim(),
      riskLevel: resultRuleForm.riskLevel,
      sort: resultRuleForm.sort,
    };
    if (resultRuleForm.id == null) {
      await addScaleResultRule(payload);
      ElMessage.success("结果规则已新增");
    } else {
      await updateScaleResultRule(resultRuleForm.id, payload);
      ElMessage.success("结果规则已更新");
    }
    resultRuleDialogVisible.value = false;
    await loadResultRules(vid);
  } catch (e) {
    ElMessage.error((e as Error)?.message || "保存失败，请稍后重试");
  } finally {
    resultRuleSubmitting.value = false;
  }
}

async function handleDeleteResultRule(row: any) {
  try {
    await ElMessageBox.confirm("确定删除该条结果规则吗？", "删除确认", {
      type: "warning",
      confirmButtonText: "删除",
      cancelButtonText: "取消",
      confirmButtonClass: "el-button--danger",
    });
  } catch {
    return;
  }
  try {
    await deleteScaleResultRule(row.id);
    ElMessage.success("规则已删除");
    const vid = selectedVersionId.value;
    if (vid != null) await loadResultRules(vid);
  } catch (e) {
    ElMessage.error((e as Error)?.message || "删除失败，请稍后重试");
  }
}

function resultRuleGroupRules(dimensionId: number | null): AdminScaleResultRuleVO[] {
  return resultRuleList.value.filter((r) => (r.dimensionId ?? null) === dimensionId);
}

function riskLevelTag(level?: number): { type: string; text: string } {
  const opt = RISK_LEVEL_OPTIONS.find((o) => o.value === level);
  return { type: opt?.type ?? "info", text: opt?.label ?? "—" };
}

async function handleResultRuleGroupSave(dimensionId: number | null) {
  const vid = selectedVersionId.value;
  if (vid == null) {
    ElMessage.warning("请先选择量表版本");
    return;
  }
  const rules = resultRuleGroupRules(dimensionId);
  if (rules.length === 0) {
    ElMessage.warning("该维度下暂无规则可保存");
    return;
  }
  resultRuleSaving.value = true;
  try {
    await saveScaleResultRuleBatch({
      scaleVersionId: vid,
      rules: rules.map((r) => ({
        id: r.id,
        scaleVersionId: vid,
        dimensionId: r.dimensionId ?? undefined,
        minScore: Number(r.minScore),
        maxScore: Number(r.maxScore),
        resultText: r.resultText,
        riskLevel: r.riskLevel ?? 0,
        sort: r.sort ?? 0,
      })),
    });
    ElMessage.success("该维度规则已整体保存");
    await loadResultRules(vid);
  } catch (e) {
    ElMessage.error((e as Error)?.message || "保存失败，请稍后重试");
  } finally {
    resultRuleSaving.value = false;
  }
}

function formatScore(v: number): string {
  return Number.isInteger(v) ? String(v) : String(v);
}

/* ==================== 交互：跳题规则 ==================== */
function openAddBranchRule() {
  Object.assign(branchRuleForm, { id: null, sourceQuestionId: null, sourceOptionId: null, targetQuestionId: null });
  branchRuleDialogVisible.value = true;
}

function openEditBranchRule(row: any) {
  Object.assign(branchRuleForm, {
    id: row.id,
    sourceQuestionId: row.sourceQuestionId ?? null,
    sourceOptionId: row.sourceOptionId ?? null,
    targetQuestionId: row.targetQuestionId ?? null,
  });
  branchRuleDialogVisible.value = true;
}

function handleBranchSourceChange() {
  branchRuleForm.sourceOptionId = null;
}

async function handleBranchRuleSubmit() {
  const vid = selectedVersionId.value;
  if (vid == null) {
    ElMessage.warning("请先选择量表版本");
    return;
  }
  const valid = await branchRuleFormRef.value?.validate().catch(() => false);
  if (!valid) return;
  branchRuleSubmitting.value = true;
  try {
    const payload = {
      scaleVersionId: vid,
      sourceQuestionId: Number(branchRuleForm.sourceQuestionId),
      sourceOptionId: branchRuleForm.sourceOptionId == null ? undefined : Number(branchRuleForm.sourceOptionId),
      targetQuestionId: branchRuleForm.targetQuestionId == null ? undefined : Number(branchRuleForm.targetQuestionId),
    };
    if (branchRuleForm.id == null) {
      await addScaleBranchRule(payload);
      ElMessage.success("跳题规则已新增");
    } else {
      await updateScaleBranchRule(branchRuleForm.id, payload);
      ElMessage.success("跳题规则已更新");
    }
    branchRuleDialogVisible.value = false;
    await loadBranchRules(vid);
  } catch (e) {
    ElMessage.error((e as Error)?.message || "保存失败，请稍后重试");
  } finally {
    branchRuleSubmitting.value = false;
  }
}

async function handleDeleteBranchRule(row: any) {
  try {
    await ElMessageBox.confirm("确定删除该条跳题规则吗？", "删除确认", {
      type: "warning",
      confirmButtonText: "删除",
      cancelButtonText: "取消",
      confirmButtonClass: "el-button--danger",
    });
  } catch {
    return;
  }
  try {
    await deleteScaleBranchRule(row.id);
    ElMessage.success("规则已删除");
    const vid = selectedVersionId.value;
    if (vid != null) await loadBranchRules(vid);
  } catch (e) {
    ElMessage.error((e as Error)?.message || "删除失败，请稍后重试");
  }
}

/* ==================== 交互：选项模板 ==================== */
function openAddOptionTemplate() {
  Object.assign(optionTemplateForm, { id: null, templateName: "", templateDesc: "" });
  templateOptionRows.value = [];
  addTemplateOptionRow();
  optionTemplateDialogVisible.value = true;
}

function openEditOptionTemplate(row: AdminScaleOptionTemplateVO) {
  Object.assign(optionTemplateForm, {
    id: row.id,
    templateName: row.templateName ?? "",
    templateDesc: row.templateDesc ?? "",
  });
  templateOptionRows.value = (row.items ?? []).map((it: AdminScaleOptionTemplateItemVO) => ({
    optionText: it.optionText ?? "",
    score: it.score ?? 0,
    sort: it.sort ?? 0,
  }));
  if (!templateOptionRows.value.length) addTemplateOptionRow();
  optionTemplateDialogVisible.value = true;
}

async function handleOptionTemplateSubmit() {
  const vid = selectedVersionId.value;
  if (vid == null) {
    ElMessage.warning("请先选择量表版本");
    return;
  }
  const valid = await optionTemplateFormRef.value?.validate().catch(() => false);
  if (!valid) return;
  if (!templateOptionRows.value.some((r) => r.optionText.trim())) {
    ElMessage.warning("选项模板至少需要一个选项项");
    return;
  }
  optionTemplateSubmitting.value = true;
  try {
    const payload: AdminScaleOptionTemplateDTO = {
      scaleVersionId: vid,
      templateName: optionTemplateForm.templateName.trim(),
      templateDesc: optionTemplateForm.templateDesc.trim() || undefined,
      items: buildTemplateItemPayload(),
    };
    if (optionTemplateForm.id == null) {
      await addOptionTemplate(payload);
      ElMessage.success("选项模板已新增");
    } else {
      await updateOptionTemplate(optionTemplateForm.id, payload);
      ElMessage.success("选项模板已更新");
    }
    optionTemplateDialogVisible.value = false;
    await loadOptionTemplates(vid);
  } catch (e) {
    ElMessage.error((e as Error)?.message || "保存失败，请稍后重试");
  } finally {
    optionTemplateSubmitting.value = false;
  }
}

function openApplyOptionTemplate(row: AdminScaleOptionTemplateVO) {
  optionTemplateApplySource.value = row;
  optionTemplateApplyMode.value = "REPLACE";
  optionTemplateApplyQuestionIds.value = [];
  optionTemplateApplyVisible.value = true;
}

async function handleOptionTemplateApply() {
  const source = optionTemplateApplySource.value;
  if (!source) return;
  if (!optionTemplateApplyQuestionIds.value.length) {
    ElMessage.warning("请至少选择一道目标题目");
    return;
  }
  optionTemplateApplySubmitting.value = true;
  try {
    await applyOptionTemplate(source.id, {
      questionIds: optionTemplateApplyQuestionIds.value,
      mode: optionTemplateApplyMode.value,
    });
    ElMessage.success("模板已应用到题目");
    optionTemplateApplyVisible.value = false;
    const vid = selectedVersionId.value;
    if (vid != null) {
      await Promise.all([loadQuestions(vid), loadOptionTemplates(vid)]);
    }
  } catch (e) {
    ElMessage.error((e as Error)?.message || "应用失败，请稍后重试");
  } finally {
    optionTemplateApplySubmitting.value = false;
  }
}

function openCopyOptionTemplate(row: AdminScaleOptionTemplateVO) {
  optionTemplateCopySource.value = row;
  optionTemplateCopyVersionId.value = null;
  optionTemplateCopyVisible.value = true;
}

async function handleOptionTemplateCopy() {
  const source = optionTemplateCopySource.value;
  if (!source) return;
  const targetVid = optionTemplateCopyVersionId.value;
  if (targetVid == null) {
    ElMessage.warning("请选择目标版本");
    return;
  }
  optionTemplateCopySubmitting.value = true;
  try {
    await copyOptionTemplate({ groupId: source.id, targetScaleVersionId: targetVid });
    ElMessage.success("选项模板已复制");
    optionTemplateCopyVisible.value = false;
  } catch (e) {
    ElMessage.error((e as Error)?.message || "复制失败，请稍后重试");
  } finally {
    optionTemplateCopySubmitting.value = false;
  }
}

async function handleDeleteOptionTemplate(row: AdminScaleOptionTemplateVO) {
  try {
    await ElMessageBox.confirm(
      `确定删除选项模板「${row.templateName}」吗？删除后不影响已应用到题目的选项。`,
      "删除确认",
      { type: "warning", confirmButtonText: "删除", cancelButtonText: "取消", confirmButtonClass: "el-button--danger" },
    );
  } catch {
    return;
  }
  try {
    await deleteOptionTemplate(row.id);
    ElMessage.success("选项模板已删除");
    const vid = selectedVersionId.value;
    if (vid != null) await loadOptionTemplates(vid);
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

/* ==================== 常模管理 ==================== */
.norm-split {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  min-height: 520px;
}
.norm-group-pane {
  width: 348px;
  flex-shrink: 0;
  border: 1px solid #eef1f6;
  border-radius: 10px;
  background: #fbfcfe;
  overflow: hidden;
}
.norm-group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border-bottom: 1px solid #eef1f6;
  background: #f8fafc;
}
.norm-group-title {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}
.norm-group-list {
  max-height: 520px;
  overflow-y: auto;
  padding: 10px;
}
.norm-group-item {
  padding: 10px 12px;
  border: 1px solid transparent;
  border-radius: 8px;
  margin-bottom: 8px;
  cursor: pointer;
  background: #fff;
  transition: all 0.2s ease;
}
.norm-group-item:last-child {
  margin-bottom: 0;
}
.norm-group-item:hover {
  border-color: #e0e7ff;
  background: #f8faff;
}
.norm-group-item.active {
  border-color: #6366f1;
  background: #eef2ff;
}
.ngi-main {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.ngi-name {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
  word-break: break-all;
}
.ngi-tag {
  flex-shrink: 0;
}
.ngi-sub {
  font-size: 11.5px;
  color: #64748b;
  line-height: 1.5;
  margin-bottom: 6px;
}
.ngi-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.ngi-stats {
  font-size: 11px;
  color: #6366f1;
  font-variant-numeric: tabular-nums;
}
.ngi-sort {
  font-size: 11px;
  color: #94a3b8;
}
.ngi-ops {
  margin-top: 6px;
  display: flex;
  justify-content: flex-end;
  gap: 2px;
}
.norm-detail-pane {
  flex: 1;
  min-width: 0;
  border: 1px solid #eef1f6;
  border-radius: 10px;
  overflow: hidden;
}
.norm-placeholder {
  min-height: 420px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.norm-detail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  padding: 12px 14px;
  border-bottom: 1px solid #eef1f6;
  background: #f8fafc;
}
.ndh-title {
  min-width: 0;
}
.ndh-name {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}
.ndh-sub {
  margin-left: 8px;
  font-size: 11.5px;
  color: #94a3b8;
}
.ndh-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.norm-table :deep(.el-input-number .el-input__inner) {
  text-align: left;
}
.norm-table :deep(.el-table__cell) {
  padding: 7px 4px;
}
.norm-detail-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 14px;
  border-top: 1px solid #eef1f6;
}
.norm-foot-tip {
  font-size: 11px;
  color: #94a3b8;
}
.norm-pop-row {
  display: flex;
  gap: 14px;
}
.norm-pop-item {
  flex: 1;
  min-width: 0;
}
.age-range {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
}
.age-sep {
  color: #94a3b8;
  font-size: 12px;
}

/* ==================== 结果规则 ==================== */
.rule-wrap {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.rule-group-card {
  border: 1px solid #eef1f6;
  border-radius: 10px;
  overflow: hidden;
}
.rule-group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 14px;
  background: #f8fafc;
  border-bottom: 1px solid #eef1f6;
}
.rgh-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.rgh-title {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}
.rgh-count {
  font-size: 11px;
  color: #94a3b8;
}
.rgh-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}
.score-range {
  font-weight: 600;
  color: #4f46e5;
  font-variant-numeric: tabular-nums;
}

/* ==================== 分支规则 ==================== */
.branch-option {
  display: inline-block;
  padding: 1px 10px;
  border-radius: 999px;
  background: #f0f9ff;
  color: #0369a1;
  font-size: 12px;
  border: 1px solid #e0f2fe;
}
.branch-end {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 6px;
  background: #fef2f2;
  color: #ef4444;
  font-size: 12px;
  border: 1px solid #fecaca;
}

/* ==================== 选项模板 ==================== */
.otm-wrap {
  min-height: 200px;
}
.otm-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 14px;
}
.otm-card {
  border: 1px solid #eef1f6;
  border-radius: 10px;
  background: #fbfcfe;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}
.otm-card:hover {
  border-color: #e0e7ff;
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.06);
}
.otm-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.otm-card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.otm-name {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
  word-break: break-all;
}
.otm-count {
  flex-shrink: 0;
  font-size: 11px;
  color: #94a3b8;
  background: #f1f5f9;
  border-radius: 999px;
  padding: 1px 8px;
}
.otm-card-ops {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  gap: 0;
}
.otm-desc {
  font-size: 12px;
  color: #64748b;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}
.otm-items {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-content: flex-start;
}
.otm-item {
  font-size: 12px;
  color: #475569;
  background: #eef2ff;
  border: 1px solid #e0e7ff;
  border-radius: 6px;
  padding: 2px 8px;
  line-height: 1.55;
}
.otm-foot {
  margin-top: auto;
  padding-top: 2px;
}
</style>