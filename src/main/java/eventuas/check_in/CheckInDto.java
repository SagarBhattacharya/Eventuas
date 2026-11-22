package eventuas.check_in;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;

public final class CheckInDto {

	private CheckInDto() {}

	@Schema(name = "CheckInRequest")
	public record Request(
		@NotBlank(message = "QR token is required")
		String qrToken
	) {}

	@Schema(name = "CheckIn")
	public record Response(
		UUID id,
		UUID ticketId,
		UUID checkedInBy,
		Instant checkedInAt
	) {
		public static Response from(CheckIn checkIn) {
			return new Response(
				checkIn.id(),
				checkIn.ticketId(),
				checkIn.checkedInBy(),
				checkIn.checkedInAt()
			);
		}
	}
}