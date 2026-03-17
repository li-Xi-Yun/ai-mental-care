package org.lixiyun.common.validation.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.lixiyun.common.validation.annotation.Xss;

/**
 * 自定义xss校验注解实现
 *
 * @author lixiyun
 */
public class XssValidator implements ConstraintValidator<Xss, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext constraintValidatorContext) {
        // 是检查字符串中是否包含 HTML 标记
        if(value == null){
            return true;
        }
        // 匹配 HTML 标记
        return !value.contains("(<[^<]*?>)|(<[\\s]*?/[^<]*?>)|(<[^<]*?/[\\s]*?>)");
    }

}
