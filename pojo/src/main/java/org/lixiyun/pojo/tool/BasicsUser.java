package org.lixiyun.pojo.tool;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;


@Data
public class BasicsUser implements Serializable {

    private static final long serialVersionUID = 1L;

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

}
