package org.lixiyun.common.validation.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.lixiyun.common.validation.annotation.NotReservedWord;

import java.util.Arrays;
import java.util.List;


public class ReservedWordValidator implements ConstraintValidator<NotReservedWord, String> {
    
    private List<String> reservedWords;
    
    @Override
    public void initialize(NotReservedWord constraintAnnotation) {
        reservedWords = Arrays.asList(constraintAnnotation.reservedWords());
    }
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // Let @NotBlank handle null validation
        }
        
        // Case-insensitive check
        String lowerValue = value.toLowerCase();
        return !reservedWords.contains(lowerValue);
    }
}
