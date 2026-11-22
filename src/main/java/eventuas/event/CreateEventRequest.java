package eventuas.event;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateEventRequest(
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
			return true; // @NotNull handles null cases
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
}