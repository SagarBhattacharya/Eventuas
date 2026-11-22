package eventuas.event_staff;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("event_staffs")
public record EventStaff(
	@Id UUID id,
	UUID eventId,
	UUID userId,
	Instant createdAt
) {
	public static EventStaff assign(UUID eventId, UUID userId, Instant now) {
		return new EventStaff(null, eventId, userId, now);
	}
}