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
 * Constrains a string to what BCrypt can encode: {@value #BCRYPT_MAX_PASSWORD_BYTES} UTF-8
 * <em>bytes</em>, not characters. {@code @Size(max = 72)} is insufficient because a single Unicode
 * code point can occupy up to four UTF-8 bytes.
 *
 * <p>The bound is deliberately not configurable — it is fixed by the algorithm, not by any caller.
 */
@Documented
@Constraint(validatedBy = Utf8ByteLength.Validator.class)
@Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE, TYPE_USE})
@Retention(RUNTIME)
public @interface Utf8ByteLength {
	
	int BCRYPT_MAX_PASSWORD_BYTES = 72;

	String message() default "must not exceed " + BCRYPT_MAX_PASSWORD_BYTES + " UTF-8 bytes";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	final class Validator implements ConstraintValidator<Utf8ByteLength, String> {

		@Override
		public boolean isValid(String value, ConstraintValidatorContext context) {
			return value == null
					|| value.getBytes(StandardCharsets.UTF_8).length <= BCRYPT_MAX_PASSWORD_BYTES;
		}
	}
}
