package org.lixiyun.common.aop.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 该注解的使用，必须保证方法中的第一个参数是要进行属性填充的对象或对象集合，且该对象必须有对应的getter和setter方法
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface SQLType {

    OperationType value();

    /**
     * 数据库操作类型
     */
    enum OperationType {

        /**
         * 更新操作
         */
        UPDATE,

        /**
         * 插入操作
         */
        INSERT
    }

}
