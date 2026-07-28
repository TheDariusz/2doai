package com.thedariusz.todoai.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Constrains a string by its encoded UTF-8 byte count rather than Java character count.
 *
 * <p>BCrypt accepts at most 72 password bytes. {@code @Size(max = 72)} is insufficient because a
 * single Unicode code point can occupy up to four UTF-8 bytes.
 */
@Documented
@Constraint(validatedBy = Utf8ByteLength.Validator.class)
@Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE, TYPE_USE})
@Retention(RUNTIME)
public @interface Utf8ByteLength {

	String message() default "must not exceed the maximum UTF-8 byte length";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	int max();

	final class Validator implements ConstraintValidator<Utf8ByteLength, String> {

		private int max;

		@Override
		public void initialize(Utf8ByteLength constraint) {
			max = constraint.max();
		}

		@Override
		public boolean isValid(String value, ConstraintValidatorContext context) {
			return value == null || value.getBytes(StandardCharsets.UTF_8).length <= max;
		}
	}
}
