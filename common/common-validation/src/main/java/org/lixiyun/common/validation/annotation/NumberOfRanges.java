package org.lixiyun.common.validation.annotation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import org.lixiyun.common.validation.validator.NumberOfRangesValidator;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NumberOfRangesValidator.class)
public @interface NumberOfRanges {

    String message() default "该字段的整数超过范围了";
    
    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
    
    int min() default 0;

    int max() default Integer.MAX_VALUE;
    
}