package eventuas.event;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/events")
public class EventController {

	private final EventService eventService;

	public EventController(EventService eventService) {
		this.eventService = eventService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Event create(
		@AuthenticationPrincipal Jwt jwt,
		@Valid @RequestBody CreateEventRequest request
	) {
		var userId = UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
		return eventService.createDraft(userId, request);
	}

	@GetMapping
	public List<Event> listOwned(@AuthenticationPrincipal Jwt jwt) {
		var userId = UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
		return eventService.listByOrganizer(userId);
	}

	@GetMapping("/published")
	public List<Event> listPublished() {
		return eventService.listPublished();
	}

	@PostMapping("/{eventId}/publish")
	public Event publish(
		@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID eventId
	) {
		var userId = UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
		return eventService.publish(userId, eventId);
	}

	@PostMapping("/{eventId}/close")
	public Event close(
		@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID eventId
	) {
		var userId = UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
		return eventService.close(userId, eventId);
	}
}