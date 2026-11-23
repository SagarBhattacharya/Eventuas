package eventuas.ticket;

import eventuas.check_in.QrSigner;
import eventuas.errors.AppException;
import eventuas.event.EventRepository;
import eventuas.pagination.PageQuery;
import eventuas.pagination.PagedResponse;
import eventuas.pagination.PaginationRepository;
import eventuas.ticket_tier.TicketTierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TicketService {

	private final EventRepository eventRepository;
	private final TicketTierRepository ticketTierRepository;
	private final TicketRepository ticketRepository;
	private final QrSigner qrSigner;
	private final Clock clock;
	private final PaginationRepository paginationRepository;

	public TicketService(
		EventRepository eventRepository,
		TicketTierRepository ticketTierRepository,
		TicketRepository ticketRepository,
		QrSigner qrSigner,
		Clock clock,
		PaginationRepository paginationRepository
	) {
		this.eventRepository = eventRepository;
		this.ticketTierRepository = ticketTierRepository;
		this.ticketRepository = ticketRepository;
		this.qrSigner = qrSigner;
		this.clock = clock;
		this.paginationRepository = paginationRepository;
	}

	@Transactional
	public Ticket buyTicket(UUID buyerId, UUID tierId) {
		var tier = ticketTierRepository.findById(tierId)
			.orElseThrow(AppException.TierNotFound::new);
		tier.ensurePurchasable();

		var event = eventRepository.findById(tier.eventId())
			.orElseThrow(AppException.EventNotFound::new);

		Instant now = clock.instant();
		event.ensureOpenForSales(now);

		int updated = ticketTierRepository.decrementStock(tierId);
		if (updated == 0) {
			throw new AppException.TierSoldOut();
		}

		return ticketRepository.save(Ticket.issue(buyerId, tierId, now));
	}

	@Transactional(readOnly = true)
	public PagedResponse<TicketDto.Summary> listByBuyer(
		UUID buyerId,
		TicketDto.WalletFilter filter,
		PageQuery pageQuery
	) {
		return paginationRepository.findBuyerTickets(buyerId, filter, clock.instant(), pageQuery);
	}

	public String issueQrToken(UUID buyerId, UUID ticketId) {
		var ticket = ticketRepository.findById(ticketId)
			.orElseThrow(AppException.TicketNotFound::new);

		ticket.ensureOwnedBy(buyerId);
		return qrSigner.sign(ticket.id());
	}
}