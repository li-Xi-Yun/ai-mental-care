package org.lixiyun.common.validation.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.lixiyun.common.validation.annotation.NumberOfRanges;

public class NumberOfRangesValidator implements ConstraintValidator<NumberOfRanges, Number> {

    private int minValue;
    private int maxValue;

    @Override
    public void initialize(NumberOfRanges constraintAnnotation) {
        minValue = constraintAnnotation.min();
        maxValue = constraintAnnotation.max();
    }
    
    @Override
    public boolean isValid(Number value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // Let @NotBlank handle null validation
        }

        long v = value.longValue();
        // Case-insensitive check
        return v >= minValue && v <= maxValue;
    }
}
