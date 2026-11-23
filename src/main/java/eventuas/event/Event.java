package eventuas.event;

import eventuas.errors.AppException;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

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
	Status status,
	Instant createdAt,
	Instant updatedAt
) {
	public enum Status {
		DRAFT,
		ACTIVE,
		CLOSED
	}

	public static Event draft(
		String name,
		String description,
		String venue,
		Instant startDate,
		Instant endDate,
		Instant salesStartDate,
		Instant salesEndDate,
		UUID organizerId,
		Instant now
	) {
		if (!endDate.isAfter(startDate)) {
			throw new AppException.InvalidEventWindow();
		}
		if (salesStartDate != null && salesEndDate != null
			&& !salesEndDate.isAfter(salesStartDate)) {
			throw new AppException.InvalidSalesWindow();
		}

		return new Event(
			null,
			name,
			description,
			venue,
			startDate,
			endDate,
			salesStartDate,
			salesEndDate,
			organizerId,
			Status.DRAFT,
			now,
			now
		);
	}

	public Event publish(long activeTierCount, Instant now) {
		if (this.status != Status.DRAFT) {
			throw new AppException.EventNotPublishable(this.status.name());
		}
		if (activeTierCount < 1) {
			throw new AppException.EventHasNoActiveTiers();
		}
		return withStatus(Status.ACTIVE, now);
	}

	public Event close(Instant now) {
		if (this.status == Status.CLOSED) {
			throw new AppException.EventAlreadyClosed();
		}
		return withStatus(Status.CLOSED, now);
	}

	public void ensureOwnedBy(UUID userId, String action) {
		if (!this.organizerId.equals(userId)) {
			throw new AppException.EventNotAuthorized(action);
		}
	}

	public void ensureAllowsTierManagement() {
		if (this.status == Status.CLOSED) {
			throw new AppException.EventAlreadyClosed();
		}
	}

	public void ensureOpenForSales(Instant now) {
		if (this.status != Status.ACTIVE) {
			throw new AppException.EventNotActive();
		}
		if ((salesStartDate != null && now.isBefore(salesStartDate))
			|| (salesEndDate != null && now.isAfter(salesEndDate))) {
			throw new AppException.SalesNotOpen();
		}
	}

	public void ensureOpenForCheckIn() {
		if (this.status != Status.ACTIVE) {
			throw new AppException.EventNotActive();
		}
	}

	public void ensureOpenToPublic() {
		if (this.status == Event.Status.DRAFT) {
			throw new AppException.EventNotFound();
		}
	}

	private Event withStatus(Status nextStatus, Instant now) {
		return new Event(
			id,
			name,
			description,
			venue,
			startDate,
			endDate,
			salesStartDate,
			salesEndDate,
			organizerId,
			nextStatus,
			createdAt,
			now
		);
	}
}