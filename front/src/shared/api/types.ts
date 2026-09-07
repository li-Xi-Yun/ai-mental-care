/** 后端统一响应结构 */
export interface Result<T = any> {
  code: number;
  msg: string;
  data: T;
}

/** 分页请求参数 */
export interface PageParams {
  pageNum: number;
  pageSize: number;
}

/** 分页响应结构 */
export interface PageResult<T = any> {
  records: T[];
  total: number;
  pageNum: number;
  pageSize: number;
}

/** 列表响应（后端分页接口返回 Result<PageResult<T>>） */
export type ListResult<T = any> = Result<PageResult<T>>;