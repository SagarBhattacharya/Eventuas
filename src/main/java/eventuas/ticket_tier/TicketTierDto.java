package eventuas.ticket_tier;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.UUID;

public final class TicketTierDto {

	private TicketTierDto() {}

	@Schema(name = "CreateTicketTierRequest")
	public record CreateRequest(
		@NotBlank(message = "Tier name is required")
		@Size(max = 100, message = "Tier name must not exceed 100 characters")
		String tier,

		@NotNull(message = "Description is required")
		@Size(max = 1000, message = "Description must not exceed 1000 characters")
		String description,

		@Positive(message = "Available quantity must be greater than zero")
		@Max(value = 1_000_000, message = "Available quantity cannot exceed 1M")
		long availableQty,

		@PositiveOrZero(message = "Price cannot be negative")
		@Max(value = 100_000_000, message = "Price cannot exceed 100M units")
		long price,

		@NotBlank(message = "Currency is required")
		@Size(min = 3, max = 3, message = "Currency must be exactly 3 characters")
		@ValidCurrency
		String currency
	) {}

	@Schema(name = "TicketTier")
	public record Response(
		UUID id,
		String tier,
		String description,
		long availableQty,
		long price,
		String currency,
		TicketTier.Status status,
		UUID eventId,
		Instant createdAt,
		Instant updatedAt
	) {
		public static Response from(TicketTier entity) {
			return new Response(
				entity.id(),
				entity.tier(),
				entity.description(),
				entity.availableQty(),
				entity.price(),
				entity.currency(),
				entity.status(),
				entity.eventId(),
				entity.createdAt(),
				entity.updatedAt()
			);
		}
	}
}