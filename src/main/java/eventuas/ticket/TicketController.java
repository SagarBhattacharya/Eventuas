package eventuas.ticket;

import eventuas.pagination.PageQuery;
import eventuas.pagination.PagedResponse;
import eventuas.user.CurrentUser;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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
	public TicketDto.Response buyTicket(
		@CurrentUser UUID buyerId,
		@PathVariable UUID tierId,
		@RequestHeader(value = "Idempotency-Key") String idempotencyKey
	) {
		return TicketDto.Response.from(ticketService.buyTicket(buyerId, tierId, idempotencyKey));
	}

	@GetMapping("/tickets/me")
	public PagedResponse<TicketDto.Summary> myTickets(
		@CurrentUser UUID userId,
		@ParameterObject TicketDto.WalletFilter filter,
		@ParameterObject PageQuery pageQuery
	) {
		return ticketService.listByBuyer(userId, filter, pageQuery);
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