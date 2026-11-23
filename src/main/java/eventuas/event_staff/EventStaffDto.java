package eventuas.event_staff;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;

public final class EventStaffDto {

	private EventStaffDto() {}

	@Schema(name = "AssignStaffRequest")
	public record AssignRequest(
		@NotBlank(message = "Email is required")
		@Email(message = "Valid email is required")
		String email
	) {}

	@Schema(name = "EventStaffMember")
	public record Member(
		UUID id,
		UUID eventId,
		UUID userId,
		String name,
		String email,
		Instant assignedAt
	) {}

	@Schema(name = "EventStaff")
	public record Response(
		UUID id,
		UUID eventId,
		UUID userId,
		Instant createdAt
	) {
		public static Response from(EventStaff staff) {
			return new Response(
				staff.id(),
				staff.eventId(),
				staff.userId(),
				staff.createdAt()
			);
		}
	}
}