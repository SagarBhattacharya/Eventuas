package eventuas.ticket;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("tickets")
public record Ticket(
	@Id UUID id,
	UUID purchasedBy,
	UUID tierId,
	Instant createdAt,
	Instant updatedAt
) {
	public static Ticket issue(UUID purchasedBy, UUID tierId) {
		Instant now = Instant.now();
		return new Ticket(null, purchasedBy, tierId, now, now);
	}
}