package user;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;
import java.time.Period;

public class MinAgeValidator implements ConstraintValidator<MinAge, LocalDate> {

    private int minYears;

    @Override
    public void initialize(MinAge constraintAnnotation) {
        this.minYears = constraintAnnotation.value();
    }

    @Override
    public boolean isValid(LocalDate dateOfBirth, ConstraintValidatorContext context) {
        // Null/future dates are @NotNull's and @Past's concerns, not this one.
        if (dateOfBirth == null || dateOfBirth.isAfter(LocalDate.now())) {
            return true;
        }
        return Period.between(dateOfBirth, LocalDate.now()).getYears() >= minYears;
    }
}
