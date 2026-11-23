package eventuas.ticket;

import eventuas.user.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class TicketController {

	private final TicketService ticketService;

	public TicketController(TicketService ticketService) {
		this.ticketService = ticketService;
	}

	@PostMapping("/tiers/{tierId}/buy")
	@ResponseStatus(HttpStatus.CREATED)
	public TicketDto.Response buy(
		@CurrentUser UUID userId,
		@PathVariable UUID tierId
	) {
		return TicketDto.Response.from(
			ticketService.buyTicket(userId, tierId)
		);
	}

	@GetMapping("/tickets/me")
	public List<TicketDto.Summary> myTickets(@CurrentUser UUID userId) {
		return ticketService.listByBuyer(userId);
	}

	@GetMapping("/tickets/{ticketId}/qr")
	public TicketDto.QrTokenResponse qrToken(
		@CurrentUser UUID userId,
		@PathVariable UUID ticketId
	) {
		return new TicketDto.QrTokenResponse(
			ticketService.issueQrToken(userId, ticketId)
		);
	}
}