package eventuas.ticket_tier;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record CreateTicketTierRequest(
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