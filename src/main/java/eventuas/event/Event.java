package eventuas.event;

import eventuas.errors.AppException;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Table("events")
public record Event(
	@Id UUID id,
	String name,
	String description,
	String venue,
	Instant startDate,
	Instant endDate,
	Instant salesStartDate,
	Instant salesEndDate,
	UUID organizerId,
	EventStatus status,
	Instant createdAt,
	Instant updatedAt
) {
	public static Event draft(
		String name,
		String description,
		String venue,
		Instant startDate,
		Instant endDate,
		Instant salesStartDate,
		Instant salesEndDate,
		UUID organizerId
	) {
		Instant now = Instant.now();
		return new Event(
			null, name, description, venue,
			startDate, endDate, salesStartDate, salesEndDate,
			organizerId, EventStatus.DRAFT, now, now
		);
	}

	public Event publish() {
		if (this.status != EventStatus.DRAFT) {
			throw new AppException.EventNotPublishable(this.status.toString());
		}
		return withStatus(EventStatus.ACTIVE);
	}

	public Event close() {
		if (this.status == EventStatus.CLOSED) {
			throw new AppException.EventAlreadyClosed();
		}
		return withStatus(EventStatus.CLOSED);
	}

	private Event withStatus(EventStatus nextStatus) {
		return new Event(
			id, name, description, venue,
			startDate, endDate, salesStartDate, salesEndDate,
			organizerId, nextStatus, createdAt, Instant.now()
		);
	}
}