package eventuas.ticket_tier;

import eventuas.errors.AppException;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("ticket_tiers")
public record TicketTier(
	@Id UUID id,
	String tier,
	String description,
	long availableQty,
	long price,
	String currency,
	TierStatus status,
	UUID eventId,
	Instant createdAt,
	Instant updatedAt
) {
	public static TicketTier create(
		String tier,
		String description,
		long availableQty,
		long price,
		String currency,
		UUID eventId
	) {
		Instant now = Instant.now();
		return new TicketTier(
			null,
			tier,
			description,
			availableQty,
			price,
			currency,
			TierStatus.ACTIVE,
			eventId,
			now,
			now
		);
	}

	public TicketTier close() {
		if (this.status == TierStatus.CLOSED) {
			throw new AppException.TierAlreadyClosed();
		}
		return new TicketTier(
			id,
			tier,
			description,
			availableQty,
			price,
			currency,
			TierStatus.CLOSED,
			eventId,
			createdAt,
			Instant.now()
		);
	}
}