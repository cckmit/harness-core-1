package io.harness.cvng.core.validators;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import javax.validation.Constraint;
import javax.validation.Payload;
import javax.validation.ReportAsSingleViolation;

@Documented
@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
@ReportAsSingleViolation
@Constraint(validatedBy = {UniqueIdentifierValidator.class})
public @interface UniqueIdentifierCheck {
  String message() default "same identifier is used by multiple entities";
  Class<?>[] groups() default {};
  Class<? extends Payload>[] payload() default {};
}