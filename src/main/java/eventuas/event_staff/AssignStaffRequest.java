package eventuas.event_staff;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AssignStaffRequest(
	@NotBlank(message = "Email is required")
	@Email(message = "Valid email is required")
	String email
) {}