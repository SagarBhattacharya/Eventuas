package eventuas.event;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public final class EventDto {

	private EventDto() {}

	@Schema(name = "CreateEventRequest")
	public record CreateRequest(
		@NotBlank(message = "Event name is required")
		String name,

		@NotBlank(message = "Event description is required")
		String description,

		@NotBlank(message = "Venue is required")
		String venue,

		@NotNull(message = "Start date is required")
		Instant startDate,

		@NotNull(message = "End date is required")
		Instant endDate,

		Instant salesStartDate,
		Instant salesEndDate
	) {
		@AssertTrue(message = "End date must be after start date")
		private boolean isEndDateAfterStartDate() {
			if (startDate == null || endDate == null) {
				return true;
			}
			return endDate.isAfter(startDate);
		}

		@AssertTrue(message = "Sales end date must be after sales start date")
		private boolean isSalesWindowValid() {
			if (salesStartDate == null || salesEndDate == null) {
				return true;
			}
			return salesEndDate.isAfter(salesStartDate);
		}

		public Event toDraftEvent(UUID organizerId, Instant now) {
			return Event.draft(
				name.trim(),
				description.trim(),
				venue.trim(),
				startDate,
				endDate,
				salesStartDate,
				salesEndDate,
				organizerId,
				now
			);
		}
	}

	@Schema(name = "Event")
	public record Response(
		UUID id,
		String name,
		String description,
		String venue,
		Instant startDate,
		Instant endDate,
		Instant salesStartDate,
		Instant salesEndDate,
		UUID organizerId,
		Event.Status status,
		Instant createdAt,
		Instant updatedAt
	) {
		public static Response from(Event event) {
			return new Response(
				event.id(),
				event.name(),
				event.description(),
				event.venue(),
				event.startDate(),
				event.endDate(),
				event.salesStartDate(),
				event.salesEndDate(),
				event.organizerId(),
				event.status(),
				event.createdAt(),
				event.updatedAt()
			);
		}
	}
}