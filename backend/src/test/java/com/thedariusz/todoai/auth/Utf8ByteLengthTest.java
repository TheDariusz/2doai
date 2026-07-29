package com.thedariusz.todoai.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The bound is exclusive of nothing and inclusive of {@code max} — the accepting boundary matters as
 * much as the rejecting one, because flipping {@code <=} to {@code <} would silently reject
 * legitimate passwords that sit exactly on BCrypt's 72-byte ceiling.
 */
class Utf8ByteLengthTest {

	private final Utf8ByteLength.Validator validator = new Utf8ByteLength.Validator();

	@Test
	void acceptsAValueExactlyAtTheLimit() {
		assertThat(validator.isValid("a".repeat(72), null)).isTrue();
	}

	@Test
	void acceptsAValueOneByteUnderTheLimit() {
		assertThat(validator.isValid("a".repeat(71), null)).isTrue();
	}

	@Test
	void rejectsAValueOneByteOverTheLimit() {
		assertThat(validator.isValid("a".repeat(73), null)).isFalse();
	}

	/**
	 * The whole reason this constraint exists: 18 emoji are 18 characters — {@code @Size(max = 72)}
	 * would wave them through — but 72 bytes, and a 19th pushes past what BCrypt can encode.
	 */
	@Test
	void countsBytesRatherThanCharacters() {
		assertThat("😀".repeat(18)).hasSize(36);
		assertThat(validator.isValid("😀".repeat(18), null)).isTrue();
		assertThat(validator.isValid("😀".repeat(19), null)).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = {"ą", "€", "😀"})
	void countsMultiByteCodePointsByTheirEncodedWidth(String codePoint) {
		int bytesPerCodePoint = codePoint.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
		int justFits = 72 / bytesPerCodePoint;

		assertThat(validator.isValid(codePoint.repeat(justFits), null)).isTrue();
		assertThat(validator.isValid(codePoint.repeat(justFits + 1), null)).isFalse();
	}

	/** Presence is {@code @NotBlank}'s job — the standard Bean Validation composition contract. */
	@Test
	void treatsNullAsValid() {
		assertThat(validator.isValid(null, null)).isTrue();
	}

}
