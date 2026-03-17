package org.lixiyun.common.validation.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.lixiyun.common.validation.annotation.NumberOfRanges;

public class NumberOfRangesValidator implements ConstraintValidator<NumberOfRanges, Integer> {

    private int minValue;
    private int maxValue;

    @Override
    public void initialize(NumberOfRanges constraintAnnotation) {
        minValue = constraintAnnotation.min();
        maxValue = constraintAnnotation.max();
    }
    
    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // Let @NotBlank handle null validation
        }
        
        // Case-insensitive check
        return value >= minValue && value <= maxValue;
    }
}
