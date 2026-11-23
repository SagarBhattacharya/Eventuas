package eventuas.ticket;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public final class TicketDto {

	private TicketDto() {}

	@Schema(name = "Ticket")
	public record Response(
		UUID id,
		UUID purchasedBy,
		UUID tierId,
		Instant createdAt,
		Instant updatedAt
	) {
		public static Response from(Ticket ticket) {
			return new Response(
				ticket.id(),
				ticket.purchasedBy(),
				ticket.tierId(),
				ticket.createdAt(),
				ticket.updatedAt()
			);
		}
	}

	@Schema(name = "TicketSummary")
	public record Summary(
		UUID ticketId,
		UUID eventId,
		String eventName,
		String venue,
		Instant startDate,
		Instant endDate,
		String tierName,
		long price,
		String currency,
		boolean checkedIn,
		Instant checkedInAt,
		Instant purchasedAt
	) {}

	@Schema(name = "QrTokenResponse")
	public record QrTokenResponse(String qrToken) {}
}