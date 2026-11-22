package eventuas.ticket_tier;

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
public class TicketTierController {

	private final TicketTierService ticketTierService;

	public TicketTierController(TicketTierService ticketTierService) {
		this.ticketTierService = ticketTierService;
	}

	@PostMapping("/{eventId}/tiers")
	@ResponseStatus(HttpStatus.CREATED)
	public TicketTier create(
		@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID eventId,
		@Valid @RequestBody CreateTicketTierRequest request
	) {
		var userId = UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
		return ticketTierService.createTier(userId, eventId, request);
	}

	@GetMapping("/{eventId}/tiers")
	public List<TicketTier> listOwned(
		@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID eventId
	) {
		var userId = UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
		return ticketTierService.listByOwnedEvent(userId, eventId);
	}

	@PostMapping("/{eventId}/tiers/{tierId}/close")
	public TicketTier close(
		@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID eventId,
		@PathVariable UUID tierId
	) {
		var userId = UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
		return ticketTierService.closeTier(userId, eventId, tierId);
	}

	@GetMapping("/published/{eventId}/tiers")
	public List<TicketTier> listPublished(@PathVariable UUID eventId) {
		return ticketTierService.listPublicTiers(eventId);
	}
}