/** 量表主表 VO */
export interface AdminScaleVO {
  id: number;
  scaleName: string;
  scaleCategoryId?: number | null;
  scaleCategoryName?: string | null;
  status?: number;
  allowRepeat?: number;
  coolMinutes?: number | null;
  timeLimit?: number | null;
  anonymous?: number;
  currentVersionId?: number | null;
  currentVersionNo?: string | null;
  questionCount?: number | null;
  dimensionCount?: number | null;
  createdTime?: string;
  createdByName?: string | null;
  updatedTime?: string;
  updatedByName?: string | null;
  deletedFlag?: number;
}

/** 量表版本 VO */
export interface AdminScaleVersionVO {
  id: number;
  scaleId?: number;
  versionNo?: string;
  description?: string | null;
  copyrightInfo?: string | null;
  questionCount?: number;
  dimensionCount?: number;
  isCurrent?: boolean;
  createdTime?: string;
  createdByName?: string | null;
  updatedTime?: string;
  updatedByName?: string | null;
  deletedFlag?: number;
}

/** 量表详情 VO（含版本列表） */
export interface AdminScaleDetailVO extends AdminScaleVO {
  versions?: AdminScaleVersionVO[];
}

/** 量表维度 VO */
export interface AdminScaleDimensionVO {
  id: number;
  scaleVersionId?: number;
  dimName: string;
  dimCode?: string;
  dimDesc?: string | null;
  sort?: number;
  questionCount?: number;
  createdTime?: string;
  updatedTime?: string;
}

/** 量表选项 VO */
export interface AdminScaleOptionVO {
  id?: number;
  questionId?: number;
  optionText?: string;
  score?: number | null;
  sort?: number;
}

/** 量表题目 VO */
export interface AdminScaleQuestionVO {
  id: number;
  scaleVersionId?: number;
  dimensionId?: number | null;
  dimensionName?: string | null;
  title: string;
  questionType?: number;
  sort?: number;
  scoreType?: number;
  required?: number;
  options?: AdminScaleOptionVO[];
  createdTime?: string;
  updatedTime?: string;
}

/** 量表类别 VO */
export interface AdminScaleCategoryVO {
  id: number;
  categoryName: string;
  sort?: number;
  scaleCount?: number;
  createdTime?: string;
  createdByName?: string | null;
  updatedTime?: string;
  updatedByName?: string | null;
  deletedFlag?: number;
}

/** 量表主表新增/修改 DTO */
export interface AdminScaleDTO {
  scaleName: string;
  scaleCategoryId?: number | null;
  status?: number;
  allowRepeat?: number;
  coolMinutes?: number | null;
  timeLimit?: number | null;
  anonymous?: number;
  createFirstVersion?: boolean;
  versionNo?: string;
  description?: string;
  copyrightInfo?: string;
}

/** 量表版本新增/修改 DTO */
export interface AdminScaleVersionDTO {
  id?: number;
  scaleId: number;
  versionNo: string;
  description?: string;
  copyrightInfo?: string;
}

/** 量表版本复制 DTO */
export interface AdminScaleVersionCopyDTO {
  sourceVersionId: number;
  targetScaleId: number;
  versionNo: string;
}

/** 量表维度新增/修改 DTO */
export interface AdminScaleDimensionDTO {
  id?: number;
  scaleVersionId: number;
  dimName: string;
  dimCode?: string;
  dimDesc?: string;
  sort?: number;
}

/** 选项项 DTO */
export interface AdminScaleOptionItemDTO {
  optionText: string;
  score?: number | null;
  sort?: number;
}

/** 量表题目新增/修改 DTO */
export interface AdminScaleQuestionDTO {
  id?: number;
  scaleVersionId: number;
  dimensionId?: number | null;
  title: string;
  questionType: number;
  sort?: number;
  scoreType?: number;
  required?: number;
  options?: AdminScaleOptionItemDTO[];
}

/* ==================== 常模（norm） ==================== */

/** 常模组 VO */
export interface AdminScaleNormGroupVO {
  id: number;
  scaleVersionId?: number;
  dimensionId?: number | null;
  dimensionName?: string | null;
  groupName: string;
  groupCode?: string;
  gender?: number | null;
  ageMin?: number | null;
  ageMax?: number | null;
  education?: number | null;
  occupation?: number | null;
  region?: number | null;
  normType?: number;
  mean?: number | null;
  sd?: number | null;
  source?: string | null;
  normYear?: number | null;
  sort?: number;
  normCount?: number;
  createdTime?: string;
  updatedTime?: string;
}

/** 常模组新增/修改 DTO */
export interface AdminScaleNormGroupDTO {
  id?: number;
  scaleVersionId: number;
  dimensionId?: number | null;
  groupName: string;
  groupCode?: string;
  gender?: number | null;
  ageMin?: number | null;
  ageMax?: number | null;
  education?: number | null;
  occupation?: number | null;
  region?: number | null;
  normType: number;
  mean?: number | null;
  sd?: number | null;
  source?: string | null;
  normYear?: number | null;
  sort?: number;
}

/** 常模明细 VO */
export interface AdminScaleNormVO {
  id: number;
  normGroupId?: number;
  dimensionId?: number | null;
  rawScore: number;
  tScore?: number | null;
  zScore?: number | null;
  percentile?: number | null;
  stanine?: number | null;
  diq?: number | null;
  levelLabel?: number | null;
  createdTime?: string;
  updatedTime?: string;
}

/** 常模明细新增/修改 DTO */
export interface AdminScaleNormDTO {
  id?: number;
  normGroupId: number;
  dimensionId?: number | null;
  rawScore: number;
  tScore?: number | null;
  zScore?: number | null;
  percentile?: number | null;
  stanine?: number | null;
  diq?: number | null;
  levelLabel?: number | null;
}

/** 常模明细批量保存 DTO */
export interface AdminScaleNormBatchDTO {
  normGroupId: number;
  items: AdminScaleNormDTO[];
}

/** 常模明细批量删除 DTO */
export interface AdminScaleNormDeleteDTO {
  normGroupId: number;
  normIds: number[];
}

/* ==================== 结果规则（result-rule） ==================== */

/** 结果规则 VO */
export interface AdminScaleResultRuleVO {
  id: number;
  scaleVersionId?: number;
  dimensionId?: number | null;
  dimensionName?: string | null;
  minScore: number;
  maxScore: number;
  resultText: string;
  riskLevel?: number;
  sort?: number;
  createdTime?: string;
  updatedTime?: string;
}

/** 结果规则新增/修改 DTO */
export interface AdminScaleResultRuleDTO {
  id?: number;
  scaleVersionId: number;
  dimensionId?: number | null;
  minScore: number;
  maxScore: number;
  resultText: string;
  riskLevel: number;
  sort?: number;
}

/** 结果规则整体保存 DTO */
export interface AdminScaleResultRuleBatchDTO {
  scaleVersionId: number;
  rules: AdminScaleResultRuleDTO[];
}

/* ==================== 跳题规则（branch-rule） ==================== */

/** 跳题规则 VO */
export interface AdminScaleBranchRuleVO {
  id: number;
  scaleVersionId?: number;
  sourceQuestionId: number;
  sourceQuestionTitle?: string | null;
  sourceOptionId?: number | null;
  sourceOptionText?: string | null;
  targetQuestionId?: number | null;
  targetQuestionTitle?: string | null;
  createdTime?: string;
  updatedTime?: string;
}

/** 跳题规则新增/修改 DTO（targetQuestionId 为空表示结束测评） */
export interface AdminScaleBranchRuleDTO {
  id?: number;
  scaleVersionId: number;
  sourceQuestionId: number;
  sourceOptionId?: number | null;
  targetQuestionId?: number | null;
}

/** 跳题规则整体保存 DTO */
export interface AdminScaleBranchRuleBatchDTO {
  scaleVersionId: number;
  rules: AdminScaleBranchRuleDTO[];
}
