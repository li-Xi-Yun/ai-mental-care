package org.lixiyun.common.sql.handler;

import cn.hutool.http.HttpStatus;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.common.core.error.exception.MyBatisException;

import java.util.Optional;

/**
 * MP注入处理器
 *
 * @author lixiyun
 * @date 2025/9/25
 */
@Slf4j
//@Component
public class InjectionMetaObjectHandler implements MetaObjectHandler {

    private static final String CREATED_BY = "createdBy";
    private static final String UPDATED_BY = "updatedBy";

    /**
     * 插入填充方法，用于在插入数据时自动填充实体对象中的创建时间、更新时间、创建人、更新人等信息
     *
     * @param metaObject 元对象，用于获取原始对象并进行填充
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        try {
            if (!isFieldHasValue(metaObject, CREATED_BY)) {
                Long userId = getCurrentId(metaObject);
                this.strictInsertFill(metaObject, CREATED_BY, Long.class, userId);
            }
            if (!isFieldHasValue(metaObject, UPDATED_BY)) {
                Long userId = getCurrentId(metaObject);
                this.strictInsertFill(metaObject, UPDATED_BY, Long.class, userId);
            }
        } catch (Exception e) {
            throw new MyBatisException(HttpStatus.HTTP_UNAUTHORIZED, "自动注入异常 => " + e.getMessage());
        }
    }


    /**
     * 更新填充方法，用于在更新数据时自动填充实体对象中的更新时间和更新人信息
     *
     * @param metaObject 元对象，用于获取原始对象并进行填充
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        try {
            if (!isFieldHasValue(metaObject, UPDATED_BY)) {
                Long userId = getCurrentId(metaObject);
                this.strictUpdateFill(metaObject, UPDATED_BY, Long.class, userId);
            }
        } catch (Exception e) {
            throw new MyBatisException(HttpStatus.HTTP_UNAUTHORIZED, "自动注入异常 => " + e.getMessage());
        }
    }

    /**
     * 获取当前用户ID, 如果未登录则使用默认值1L
     * @param metaObject 元对象，用于获取原始对象
     * @return 当前用户ID
     */
    private Long getCurrentId(MetaObject metaObject){
        Optional<Long> currentIdOpl = UserInfoThreadLocalUtil.getCurrentId();
        return currentIdOpl.orElseGet(
                () -> {
                    // 尝试从当前对象获取ID（如果已经设置了的话）
                    Object idObj = getFieldValByName("id", metaObject);
                    if (idObj != null) {
                        try {
                            return Long.parseLong(idObj.toString());
                        } catch (NumberFormatException e) {
                            // 如果转换失败，使用默认值
                            return 1L;
                        }
                    } else {
                        // 如果ID也没有设置，使用默认值
                        return 1L;
                    }
                }
        );
    }

    /**
     * 核心新增方法：判断指定字段是否已有值（非null）
     * @param metaObject 元对象
     * @param fieldName  字段名
     * @return true=字段有值，false=字段无值（需填充）
     */
    private boolean isFieldHasValue(MetaObject metaObject, String fieldName) {
        // 1. 先检查字段是否存在（避免字段不存在导致的异常）
        if (!metaObject.hasGetter(fieldName)) {
            return false;
        }
        // 2. 获取字段当前值，判断是否非null
        Object fieldValue = getFieldValByName(fieldName, metaObject);
        return fieldValue != null;
    }

}
