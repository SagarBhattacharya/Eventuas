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
	Status status,
	UUID eventId,
	Instant createdAt,
	Instant updatedAt
) {
	public enum Status {
		ACTIVE,
		CLOSED
	}

	public static TicketTier create(
		String tier,
		String description,
		long availableQty,
		long price,
		String currency,
		UUID eventId,
		Instant now
	) {
		if (availableQty <= 0 || price < 0) {
			throw new IllegalArgumentException("Invalid tier quantity or price");
		}
		return new TicketTier(
			null,
			tier,
			description,
			availableQty,
			price,
			currency,
			Status.ACTIVE,
			eventId,
			now,
			now
		);
	}

	public TicketTier close(Instant now) {
		if (this.status == Status.CLOSED) {
			throw new AppException.TierAlreadyClosed();
		}
		return new TicketTier(
			id,
			tier,
			description,
			availableQty,
			price,
			currency,
			Status.CLOSED,
			eventId,
			createdAt,
			now
		);
	}

	public void ensurePurchasable() {
		if (this.status != Status.ACTIVE) {
			throw new AppException.TierAlreadyClosed();
		}
		if (this.availableQty <= 0) {
			throw new AppException.TierSoldOut();
		}
	}
}