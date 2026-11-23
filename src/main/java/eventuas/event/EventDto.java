package eventuas.event;

import eventuas.ticket_tier.TicketTier;
import eventuas.ticket_tier.TicketTierDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
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

	@Schema(name = "PublicEventDetail")
	public record PublicDetail(
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
		Instant updatedAt,
		List<TicketTierDto.Response> tiers
	) {
		public static PublicDetail from(Event event, List<TicketTier> tiers) {
			return new PublicDetail(
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
				event.updatedAt(),
				tiers.stream().map(TicketTierDto.Response::from).toList()
			);
		}
	}

	@Schema(name = "TicketTierStats")
	public record TierStats(
		UUID tierId,
		String tierName,
		TicketTier.Status status,
		long price,
		String currency,
		long soldCount,
		long remainingQty,
		long checkedInCount,
		long revenue
	) {}

	@Schema(name = "EventStats")
	public record Stats(
		UUID eventId,
		String eventName,
		Event.Status status,
		long totalCapacity,
		long ticketsSold,
		long ticketsRemaining,
		long checkedInCount,
		List<TierStats> tiers
	) {
		public static Stats from(Event event, List<TierStats> tiers) {
			long sold = tiers.stream().mapToLong(TierStats::soldCount).sum();
			long remaining = tiers.stream().mapToLong(TierStats::remainingQty).sum();
			long checkedIn = tiers.stream().mapToLong(TierStats::checkedInCount).sum();

			return new Stats(
				event.id(),
				event.name(),
				event.status(),
				sold + remaining,
				sold,
				remaining,
				checkedIn,
				tiers
			);
		}
	}

	@Schema(name = "PublicEventFilter")
	public record PublicFilter(
		@Schema(description = "Case-insensitive search across name, description, and venue")
		String q,

		@Schema(description = "Filter by public status (ACTIVE or CLOSED)")
		Event.Status status,

		@Schema(description = "Include events ending on or after this timestamp (ISO-8601)")
		Instant from,

		@Schema(description = "Include events starting on or before this timestamp (ISO-8601)")
		Instant to
	) {
		public static PublicFilter empty() {
			return new PublicFilter(null, null, null, null);
		}
	}

	@Schema(name = "OrganizerEventFilter")
	public record OrganizerFilter(
		@Schema(description = "Case-insensitive search across name, description, and venue")
		String q,

		@Schema(description = "Filter by status (DRAFT, ACTIVE, or CLOSED)")
		Event.Status status,

		@Schema(description = "Include events ending on or after this timestamp (ISO-8601)")
		Instant from,

		@Schema(description = "Include events starting on or before this timestamp (ISO-8601)")
		Instant to
	) {
		public static OrganizerFilter empty() {
			return new OrganizerFilter(null, null, null, null);
		}
	}
}