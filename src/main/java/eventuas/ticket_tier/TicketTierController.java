package eventuas.ticket_tier;

import eventuas.user.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/events")
public class TicketTierController {

	private final TicketTierService ticketTierService;

	public TicketTierController(TicketTierService ticketTierService) {
		this.ticketTierService = ticketTierService;
	}

	@PostMapping("/{eventId}/tiers")
	@ResponseStatus(HttpStatus.CREATED)
	public TicketTierDto.Response create(
		@CurrentUser UUID userId,
		@PathVariable UUID eventId,
		@Valid @RequestBody TicketTierDto.CreateRequest request
	) {
		var created = ticketTierService.createTier(
			userId,
			eventId,
			request.tier().trim(),
			request.description().trim(),
			request.availableQty(),
			request.price(),
			request.currency()
		);
		return TicketTierDto.Response.from(created);
	}

	@GetMapping("/{eventId}/tiers")
	public List<TicketTierDto.Response> listOwned(
		@CurrentUser UUID userId,
		@PathVariable UUID eventId
	) {
		return ticketTierService.listByOwnedEvent(userId, eventId)
			.stream()
			.map(TicketTierDto.Response::from)
			.toList();
	}

	@PostMapping("/{eventId}/tiers/{tierId}/close")
	public TicketTierDto.Response close(
		@CurrentUser UUID userId,
		@PathVariable UUID eventId,
		@PathVariable UUID tierId
	) {
		return TicketTierDto.Response.from(
			ticketTierService.closeTier(userId, eventId, tierId)
		);
	}

	@GetMapping("/published/{eventId}/tiers")
	public List<TicketTierDto.Response> listPublished(@PathVariable UUID eventId) {
		return ticketTierService.listPublicTiers(eventId)
			.stream()
			.map(TicketTierDto.Response::from)
			.toList();
	}
}