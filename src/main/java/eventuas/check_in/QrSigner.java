package eventuas.check_in;

import eventuas.errors.AppException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;

@Component
public class QrSigner {

	private static final String HMAC_ALG = "HmacSHA256";
	private final SecretKeySpec keySpec;

	public QrSigner(@Value("${app.qr.secret}") String secret) {
		this.keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALG);
	}

	public String sign(UUID ticketId) {
		String payload = ticketId.toString();
		String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(payload));
		return payload + "." + signature;
	}

	public UUID verifyAndExtractTicketId(String qrToken) {
		if (qrToken == null || qrToken.isBlank()) {
			throw new AppException.InvalidQrToken();
		}

		int dotIdx = qrToken.indexOf('.');
		if (dotIdx <= 0 || dotIdx != qrToken.lastIndexOf('.')) {
			throw new AppException.InvalidQrToken();
		}

		String rawId = qrToken.substring(0, dotIdx);
		String providedSig = qrToken.substring(dotIdx + 1);

		UUID ticketId;
		try {
			ticketId = UUID.fromString(rawId);
		} catch (IllegalArgumentException ex) {
			throw new AppException.InvalidQrToken();
		}

		byte[] expectedSigBytes = hmac(ticketId.toString());
		byte[] providedSigBytes;
		try {
			providedSigBytes = Base64.getUrlDecoder().decode(providedSig);
		} catch (IllegalArgumentException ex) {
			throw new AppException.InvalidQrToken();
		}

		if (!MessageDigest.isEqual(expectedSigBytes, providedSigBytes)) {
			throw new AppException.InvalidQrToken();
		}

		return ticketId;
	}

	private byte[] hmac(String data) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALG);
			mac.init(keySpec);
			return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
		} catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Failed to compute QR HMAC", ex);
		}
	}
}