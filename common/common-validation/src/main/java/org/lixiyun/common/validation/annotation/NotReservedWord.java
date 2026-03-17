package org.lixiyun.common.validation.annotation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import org.lixiyun.common.validation.validator.ReservedWordValidator;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PARAMETER;

@Target({ElementType.FIELD, PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ReservedWordValidator.class)
public @interface NotReservedWord {

    String message() default "不能使用保留字，如null";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
    
    String[] reservedWords() default {"null", "NaN", "default", "undefined"};
}
