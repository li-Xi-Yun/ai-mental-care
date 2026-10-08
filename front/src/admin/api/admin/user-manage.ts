import httpClient from "@shared/api/instance";
import type { Result, ListResult } from "@shared/api/types";

/** 系统用户分页查询参数 */
export interface SysUserPageParams {
  pageNum: number;
  pageSize: number;
  keyword?: string;
  status?: number;
}

/** 系统用户分页列表 VO */
export interface SysUserPageVO {
  id: number;
  /** 用户账号（登录名） */
  loginAccount?: string;
  /** 用户名（显示名） */
  username: string;
  /** 性别：0=女，1=男，2=未知 */
  gender?: number;
  /** 头像路径 */
  avatar?: string;
  /** 账号状态：0=正常，1=异常，2=封禁，3=注销 */
  status: number;
  /** 注册时间 */
  createdTime?: string;
  /** 更新时间 */
  updatedTime?: string;
}

/** 系统用户详情 VO（含封禁记录） */
export interface SysUserDetailVO {
  id: number;
  /** 用户账号（登录名） */
  loginAccount?: string;
  /** 用户名（显示名） */
  username?: string;
  /** 用户简介 */
  introduction?: string;
  /** 邮箱 */
  email?: string;
  /** 手机号码 */
  mobile?: string;
  /** 性别：0=女，1=男，2=未知 */
  gender?: number;
  /** 头像路径 */
  avatar?: string;
  /** 账号状态：0=正常，1=异常，2=封禁，3=注销 */
  status?: number;
  /** 封禁理由 */
  banReason?: string;
  /** 封禁开始时间 */
  banTime?: string;
  /** 封禁结束时间（null 表示永久封禁） */
  banEndTime?: string;
  /** 注册时间 */
  createdTime?: string;
  /** 更新时间 */
  updatedTime?: string;
}

/** 用户状态更新 DTO（0正常 / 1异常 / 2封禁 / 3注销） */
export interface SysUserStatusUpdateDTO {
  status: number;
  /** 封禁理由（封禁时必填） */
  banReason?: string;
  /** 封禁开始时间（不传默认当前时间） */
  banTime?: string;
  /** 封禁结束时间（非永久封禁时必填） */
  banEndTime?: string;
}

/** 角色 VO */
export interface RoleVO {
  id: number;
  roleKey?: string;
  name?: string;
  status?: number;
  remark?: string;
}

/** 可选角色（下拉选择用） */
export interface SimpleRole {
  id: number;
  name: string;
}

/** 临时权限记录 VO */
export interface TempPermissionVO {
  id: number;
  personId?: number;
  personUsername?: string;
  permissionId?: number;
  /** 权限标识符 */
  perms?: string;
  /** 权限名称 */
  permissionName?: string;
  /** 权限分组名称 */
  groupName?: string;
  /** 生效时间 */
  startTime?: string;
  /** 过期时间 */
  expireTime?: string;
  /** 状态：0=有效，1=手动作废，2=已过期 */
  status?: number;
  /** 授予原因 */
  grantReason?: string;
  grantUserId?: number;
  grantUsername?: string;
  createdTime?: string;
}

/** 分页查询用户列表（POST /admin/sysUser/page） */
export function getUserPage(data: SysUserPageParams) {
  return httpClient.post<ListResult<SysUserPageVO>>("/admin/sysUser/page", data);
}

/** 查询单个用户详情（GET /admin/sysUser/{id}） */
export function getUserDetail(id: number) {
  return httpClient.get<Result<SysUserDetailVO>>(`/admin/sysUser/${id}`);
}

/** 修改用户账号状态（PUT /admin/sysUser/status/{id}） */
export function updateUserStatus(id: number, data: SysUserStatusUpdateDTO) {
  return httpClient.put<Result<void>>(`/admin/sysUser/status/${id}`, data);
}

/** 查询用户已分配的角色列表（GET /admin/sysUser/{id}/roles） */
export function getUserRoles(id: number) {
  return httpClient.get<Result<RoleVO[]>>(`/admin/sysUser/${id}/roles`);
}

/**
 * 给用户分配角色（PUT /admin/sysUser/{id}/assignRoles，全程覆盖）
 * 后端为 @RequestParam List<Long>，故将 roleIdList 走 query 并以无下标形式序列化，
 * 得到 roleIdList=1&roleIdList=2，兼容 Spring 的 List 参数绑定。
 */
export function assignUserRoles(id: number, roleIdList: number[]) {
  return httpClient.put<Result<void>>(`/admin/sysUser/${id}/assignRoles`, null, {
    params: { roleIdList },
    paramsSerializer: { indexes: null },
  });
}

/** 查询用户临时权限记录（GET /admin/sysUser/{id}/tempPerms） */
export function getUserTempPermissions(id: number) {
  return httpClient.get<Result<TempPermissionVO[]>>(`/admin/sysUser/${id}/tempPerms`);
}

/** 获取可选角色列表（下拉选择，GET /admin/sysRole/allSimple） */
export function getAllSimpleRoles() {
  return httpClient.get<Result<SimpleRole[]>>("/admin/sysRole/allSimple");
}