package eventuas.check_in;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QrSignerTest {

	@Test
	void rejectsNullBlankOrShortSecrets() {
		assertThatThrownBy(() -> new QrSigner(null))
			.isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> new QrSigner("   "))
			.isInstanceOf(IllegalArgumentException.class);

		// 31 ASCII bytes -> 1 byte short of 256-bit minimum
		String thirtyOneBytes = "a".repeat(31);
		assertThatThrownBy(() -> new QrSigner(thirtyOneBytes))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void signsAndVerifiesWhenSecretMeetsMinimumLength() {
		String thirtyTwoBytes = "a".repeat(32);
		var signer = new QrSigner(thirtyTwoBytes);

		UUID ticketId = UUID.randomUUID();
		String token = signer.sign(ticketId);

		assertThat(signer.verifyAndExtractTicketId(token)).isEqualTo(ticketId);
	}
}