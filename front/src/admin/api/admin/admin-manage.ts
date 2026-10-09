import httpClient from "@shared/api/instance";
import type { Result, ListResult } from "@shared/api/types";

/** 管理员分页查询参数 */
export interface AdminPageParams {
  pageNum: number;
  pageSize: number;
  keyword?: string;
  status?: number;
}

/** 管理员分页列表 VO */
export interface AdminPageVO {
  id: number;
  loginAccount?: string;
  username: string;
  email?: string;
  mobile?: string;
  status: number;
  createdTime?: string;
  updatedTime?: string;
}

/** 新增管理员 DTO */
export interface AdminAddDTO {
  username: string;
  password?: string;
  mobile?: string;
  email?: string;
  sysPassword?: boolean | null;
}

/** 修改管理员基础信息 DTO */
export interface AdminUpdateDTO {
  id: number;
  username: string;
  mobile?: string;
  email?: string;
}

/** 修改管理员状态 DTO */
export interface AdminStatusUpdateDTO {
  status: number;
  banTime?: string;
  banEndTime?: string;
  banReason?: string;
}

/** 管理员详情 VO（含封禁信息，用于状态徽章 tooltip） */
export interface AdminDetailVO {
  id: number;
  loginAccount?: string;
  username?: string;
  email?: string;
  mobile?: string;
  status?: number;
  banTime?: string;
  banEndTime?: string;
  banReason?: string;
  createdTime?: string;
  updatedTime?: string;
}

/** 角色 VO */
export interface RoleVO {
  id: number;
  roleKey?: string;
  name?: string;
  status?: number;
  remark?: string;
}

/** 可选角色（下拉用） */
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

/** 分页查询管理员列表 */
export function getAdminPage(data: AdminPageParams) {
  return httpClient.post<ListResult<AdminPageVO>>("/admin/sysAdmin/page", data);
}

/** 新增管理员 */
export function addAdmin(data: AdminAddDTO) {
  return httpClient.post<Result<void>>("/admin/sysAdmin/add", data);
}

/** 修改管理员基础信息 */
export function updateAdmin(data: AdminUpdateDTO) {
  return httpClient.put<Result<void>>("/admin/sysAdmin/update", data);
}

/** 修改管理员账号状态 */
export function updateAdminStatus(id: number, data: AdminStatusUpdateDTO) {
  return httpClient.put<Result<void>>(`/admin/sysAdmin/status/${id}`, data);
}

/** 重置管理员密码为默认密码 */
export function resetAdminPwd(id: number) {
  return httpClient.put<Result<void>>(`/admin/sysAdmin/resetPwd/${id}`);
}

/** 注销管理员 */
export function deleteAdmin(id: number) {
  return httpClient.delete<Result<void>>(`/admin/sysAdmin/${id}`);
}

/** 查询管理员详情 */
export function getAdminDetail(id: number) {
  return httpClient.get<Result<AdminDetailVO>>(`/admin/sysAdmin/${id}`);
}

/** 查询管理员已分配的角色列表 */
export function getAdminRoles(id: number) {
  return httpClient.get<Result<RoleVO[]>>(`/admin/sysAdmin/${id}/roles`);
}

/** 给管理员分配角色（全量覆盖） */
export function assignAdminRoles(id: number, roleIdList: number[]) {
  return httpClient.put<Result<void>>(`/admin/sysAdmin/${id}/assignRoles`, roleIdList);
}

/** 查询管理员临时权限记录（status 不传默认查询所有状态） */
export function getAdminTempPermissions(id: number, status?: number) {
  return httpClient.get<Result<TempPermissionVO[]>>(`/admin/sysAdmin/${id}/tempPerms`, {
    params: { status },
  });
}

/** 获取可选角色列表（下拉选择） */
export function getAllSimpleRoles() {
  return httpClient.get<Result<SimpleRole[]>>("/admin/sysRole/allSimple");
}
