package eventuas.event;

import eventuas.pagination.PageQuery;
import eventuas.pagination.PagedResponse;
import eventuas.user.CurrentUser;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
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
	public PagedResponse<EventDto.Response> listOwned(
		@CurrentUser UUID userId,
		@ParameterObject EventDto.OrganizerFilter filter,
		@ParameterObject PageQuery pageQuery
	) {
		return eventService.listByOrganizer(userId, filter, pageQuery);
	}

	@GetMapping("/published")
	public PagedResponse<EventDto.Response> listPublished(
		@ParameterObject EventDto.PublicFilter filter,
		@ParameterObject PageQuery pageQuery
	) {
		return eventService.listPublished(filter, pageQuery);
	}

	@GetMapping("/published/{eventId}")
	public EventDto.PublicDetail getPublished(@PathVariable UUID eventId) {
		return eventService.getPublishedDetail(eventId);
	}

	@GetMapping("/{eventId}/stats")
	public EventDto.Stats stats(
		@CurrentUser UUID userId,
		@PathVariable UUID eventId
	) {
		return eventService.getStats(userId, eventId);
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