package org.lixiyun.common.translation.annotation;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.lixiyun.common.translation.core.handler.TranslationHandler;

import java.lang.annotation.*;

/**
 * 通用翻译注解
 *
 * @author lixiyun
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
@Documented
@JacksonAnnotationsInside
// 指定了使用自定义的序列化器 TranslationHandler 来处理标记了 @Translation 注解的字段。
@JsonSerialize(using = TranslationHandler.class)
public @interface Translation {
    // 这个模块的这种翻译功能就是一张数据库表中存在其他数据库表中的id，但不想在业务代码中进行数据查询与封装
    // 就在对应的Vo中添加对应的字段信息，同时加上对应的翻译注解
    // 接着在SpringMVC的JSON转化过程中，从对应的Service层中查询数据库信息(或是直接转化)，添加到对应的Vo中的字段

    // 当字段标记了 @Translation 注解后，Jackson会在序列化时自动使用 TranslationHandler 来处理该字段

    /**
     * 类型 (需与实现类上的 {@link TranslationType} 注解type对应)
     * <p>
     * 默认取当前字段的值 如果设置了 @{@link Translation#mapper()} 则取映射字段的值
     */
    String type();

    /**
     * 映射字段 (如果不为空则取此字段的值)
     */
    String mapper() default "";

    /**
     * 其他条件 例如: 字典type(sys_user_sex)
     */
    String other() default "";

    // 现在有一个字段信息，如：gender，其中0表示女，1表示男，需要根据gender，翻译出性别名称
    // 这种就不用加Mapper字段信息，直接在对应的TranslationImpl中添加对应的翻译信息，将0翻译成女，1翻译成男

    // 现在有二个字段，一个字段是userId，另一个字段是username，需要根据userId，翻译出username
    // @Translation 注解应该添加到 username 字段上，并且需要设置 mapper 参数指向 userId 字段

    // 这个 other 的字段使用，有很多种情况，要看具体需要什么条件，如：数据库中的where查询条件


}
