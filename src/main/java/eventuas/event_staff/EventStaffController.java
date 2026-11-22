package eventuas.event_staff;

import eventuas.user.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
	public EventStaffDto.Response assign(
		@CurrentUser UUID userId,
		@PathVariable UUID eventId,
		@Valid @RequestBody EventStaffDto.AssignRequest request
	) {
		return EventStaffDto.Response.from(
			eventStaffService.assignStaff(userId, eventId, request.email())
		);
	}

	@GetMapping
	public List<EventStaffDto.Response> list(
		@CurrentUser UUID userId,
		@PathVariable UUID eventId
	) {
		return eventStaffService.listStaff(userId, eventId).stream()
			.map(EventStaffDto.Response::from)
			.toList();
	}

	@DeleteMapping("/{staffUserId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void revoke(
		@CurrentUser UUID userId,
		@PathVariable UUID eventId,
		@PathVariable UUID staffUserId
	) {
		eventStaffService.revokeStaff(userId, eventId, staffUserId);
	}
}