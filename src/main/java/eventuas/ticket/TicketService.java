package eventuas.ticket;

import eventuas.check_in.QrSigner;
import eventuas.errors.AppException;
import eventuas.event.EventRepository;
import eventuas.event.EventStatus;
import eventuas.ticket_tier.TicketTierRepository;
import eventuas.ticket_tier.TierStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TicketService {

	private final EventRepository eventRepository;
	private final TicketTierRepository ticketTierRepository;
	private final TicketRepository ticketRepository;
	private final QrSigner qrSigner;

	public TicketService(
		EventRepository eventRepository,
		TicketTierRepository ticketTierRepository,
		TicketRepository ticketRepository,
		QrSigner qrSigner
	) {
		this.eventRepository = eventRepository;
		this.ticketTierRepository = ticketTierRepository;
		this.ticketRepository = ticketRepository;
		this.qrSigner = qrSigner;
	}

	@Transactional
	public Ticket buyTicket(UUID buyerId, UUID tierId) {
		var tier = ticketTierRepository.findById(tierId)
			.orElseThrow(AppException.TierNotFound::new);

		if (tier.status() == TierStatus.CLOSED) {
			throw new AppException.TierAlreadyClosed();
		}

		var event = eventRepository.findById(tier.eventId())
			.orElseThrow(AppException.EventNotFound::new);

		if (event.status() != EventStatus.ACTIVE) {
			throw new AppException.EventNotActive();
		}

		int updated = ticketTierRepository.decrementStock(tierId);
		if (updated == 0) {
			throw new AppException.TierSoldOut();
		}

		var ticket = Ticket.issue(buyerId, tierId);
		return ticketRepository.save(ticket);
	}

	public List<Ticket> listByBuyer(UUID buyerId) {
		return ticketRepository.findByPurchasedByOrderByCreatedAtDesc(buyerId);
	}

	public QrTokenResponse issueQrToken(UUID buyerId, UUID ticketId) {
		var ticket = ticketRepository.findById(ticketId)
			.orElseThrow(AppException.TicketNotFound::new);

		if (!buyerId.equals(ticket.purchasedBy())) {
			throw new AppException.TicketNotOwned();
		}

		return new QrTokenResponse(qrSigner.sign(ticket.id()));
	}
}