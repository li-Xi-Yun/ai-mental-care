package org.lixiyun.pojo.tool;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;


@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class BasicsUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户状态 0-正常 */
    public static final int USER_STATUS_NORMAL = 0;
    /** 用户状态 1-异常 */
    public static final int USER_STATUS_ABNORMAL = 1;
    /** 用户状态 2-封禁 */
    public static final int USER_STATUS_BAN = 2;
    /** 用户状态 3-注销 */
    public static final int USER_STATUS_LOGOUT = 3;

    /**
     * 用户唯一ID，由子类进行自定义
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 账号状态（0正常 1异常 2封禁 3注销）
     */
    private Integer status;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码哈希值
     */
    private String password;

    /**
     * 是否封禁
     */
    public boolean isBanned() {
        return status != null && status == BasicsUser.USER_STATUS_BAN;
    }

    /**
     * 是否注销
     */
    public boolean isLogout() {
        return status != null && status == BasicsUser.USER_STATUS_LOGOUT;
    }

    /**
     * 是否正常
     */
    public boolean isNormal() {
        return status != null && status == BasicsUser.USER_STATUS_NORMAL;
    }

    /**
     * 是否异常
     */
    public boolean isAbnormal() {
        return status != null && status == BasicsUser.USER_STATUS_ABNORMAL;
    }

}
