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
