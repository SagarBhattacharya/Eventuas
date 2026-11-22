package eventuas.event_staff;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/events/{eventId}/staff")
public class EventStaffController {

	private final EventStaffService eventStaffService;

	public EventStaffController(EventStaffService eventStaffService) {
		this.eventStaffService = eventStaffService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public EventStaff assign(
		@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID eventId,
		@Valid @RequestBody AssignStaffRequest request
	) {
		var userId = UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
		return eventStaffService.assignStaff(userId, eventId, request.email());
	}

	@GetMapping
	public List<EventStaff> list(
		@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID eventId
	) {
		var userId = UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
		return eventStaffService.listStaff(userId, eventId);
	}

	@DeleteMapping("/{staffUserId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void revoke(
		@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID eventId,
		@PathVariable UUID staffUserId
	) {
		var userId = UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
		eventStaffService.revokeStaff(userId, eventId, staffUserId);
	}
}