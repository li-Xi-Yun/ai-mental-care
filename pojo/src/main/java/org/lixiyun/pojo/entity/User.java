package org.lixiyun.pojo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lixiyun.pojo.tool.BasicsUser;

import java.io.Serializable;
import java.time.LocalDateTime;


/**
 * 用户信息表(UserProfile)实体类
 *
 * @author lixiyun
 * @since 2025-12-13 22:39:54
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class User extends BasicsUser implements Serializable {

    private static final long serialVersionUID = 796166110583988304L;

    /**
     * 用户简介
     */
    private String introduction;
    /**
     * 邮箱号
     */
    private String email;
    /**
     * 手机号
     */
    private String mobile;
    /**
     * 性别，0：女，1：男,2：未知
     */
    private Integer gender;
    /**
     * 用户头像路径
     */
    private String avatar;
    /**
     * 封禁时间，表示到这时进行解封
     */
    private LocalDateTime banTime;

    /**
     * 最后更新时间
     */
    private LocalDateTime updatedTime;
    
    /**
     * 注册时间
     */
    private LocalDateTime createdTime;
    
    /**
     * 最后更新所属id
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    /**
     * 逻辑删除，0：未删除，1：已删除
     */
    @TableLogic
    private Integer deleted;

}

