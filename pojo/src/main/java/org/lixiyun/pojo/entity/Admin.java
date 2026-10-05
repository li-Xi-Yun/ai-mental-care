package org.lixiyun.pojo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.lixiyun.pojo.tool.BasicsUser;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理员信息表(Admin)实体类
 *
 * @author lixiyun
 * @since 2026-04-18 20:46:38
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class Admin extends BasicsUser implements Serializable {

    private static final long serialVersionUID = 340264624662932328L;

    /**
     * 用户邮箱
     */
    private String email;

    /**
     * 手机号码
     */
    private String mobile;

    /**
     * 账号名，创建时系统自动生成，之后由超级管理员修改
     */
    private String loginAccount;

    /**
     * 账号名修改时间
     */
    private LocalDateTime loginAccountUpdateTime;

    /**
     * 封禁开始时间
     */
    private LocalDateTime banTime;

    /**
     * 封禁结束时间，表示到这时进行解封
     */
    private LocalDateTime banEndTime;

    /**
     * 封禁原因
     */
    private String banReason;

    /**
     * 注册时间
     */
    private LocalDateTime createdTime;

    /**
     * 创建人用户id
     */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /**
     * 更新者
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    /**
     * 更新时间
     */
    private LocalDateTime updatedTime;

}

