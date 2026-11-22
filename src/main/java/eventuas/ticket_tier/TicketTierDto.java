package eventuas.ticket_tier;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.UUID;

public final class TicketTierDto {

	private TicketTierDto() {}

	@Schema(name = "CreateTicketTierRequest")
	public record CreateRequest(
		@NotBlank(message = "Tier name is required")
		String tier,

		@NotNull(message = "Description is required")
		String description,

		@Positive(message = "Available quantity must be greater than zero")
		long availableQty,

		@Min(value = 0, message = "Price cannot be negative")
		long price,

		@NotBlank(message = "Currency is required")
		@Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter uppercase ISO code")
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