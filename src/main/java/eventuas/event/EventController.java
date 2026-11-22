package eventuas.event;

import eventuas.user.CurrentUser;
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
	public EventDto.Response create(
		@CurrentUser UUID userId,
		@Valid @RequestBody EventDto.CreateRequest request
	) {
		var event = eventService.createDraft(
			userId,
			request.name().trim(),
			request.description().trim(),
			request.venue().trim(),
			request.startDate(),
			request.endDate(),
			request.salesStartDate(),
			request.salesEndDate()
		);
		return EventDto.Response.from(event);
	}

	@GetMapping
	public List<EventDto.Response> listOwned(
		@CurrentUser UUID userId
	) {
		return eventService.listByOrganizer(userId).stream()
			.map(EventDto.Response::from)
			.toList();
	}

	@GetMapping("/published")
	public List<EventDto.Response> listPublished() {
		return eventService.listPublished().stream()
			.map(EventDto.Response::from)
			.toList();
	}

	@PostMapping("/{eventId}/publish")
	public EventDto.Response publish(
		@CurrentUser UUID userId,
		@PathVariable UUID eventId
	) {
		return EventDto.Response.from(
			eventService.publish(userId, eventId)
		);
	}

	@PostMapping("/{eventId}/close")
	public EventDto.Response close(
		@CurrentUser UUID userId,
		@PathVariable UUID eventId
	) {
		return EventDto.Response.from(
			eventService.close(userId, eventId)
		);
	}
}