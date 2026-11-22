package eventuas.ticket;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
public class TicketController {

	private final TicketService ticketService;

	public TicketController(TicketService ticketService) {
		this.ticketService = ticketService;
	}

	@PostMapping("/tiers/{tierId}/buy")
	@ResponseStatus(HttpStatus.CREATED)
	public Ticket buy(
		@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID tierId
	) {
		var userId = UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
		return ticketService.buyTicket(userId, tierId);
	}

	@GetMapping("/tickets/me")
	public List<Ticket> myTickets(@AuthenticationPrincipal Jwt jwt) {
		var userId = UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
		return ticketService.listByBuyer(userId);
	}

	@GetMapping("/tickets/{ticketId}/qr")
	public QrTokenResponse qrToken(
		@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID ticketId
	) {
		var userId = UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
		return ticketService.issueQrToken(userId, ticketId);
	}
}