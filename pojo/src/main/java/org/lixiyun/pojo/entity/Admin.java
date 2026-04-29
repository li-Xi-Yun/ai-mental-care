package org.lixiyun.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
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
@Builder
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
     * 更新者
     */
    private Long updatedBy;

    /**
     * 更新时间
     */
    private LocalDateTime updatedTime;

    /**
     * 是否删除，0-否，1-是
     */
    private Integer deleted;

}

