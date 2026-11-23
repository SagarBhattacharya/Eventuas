package eventuas.ticket;

import eventuas.check_in.QrSigner;
import eventuas.errors.AppException;
import eventuas.event.EventRepository;
import eventuas.pagination.PageQuery;
import eventuas.pagination.PagedResponse;
import eventuas.ticket_tier.TicketTierRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class TicketService {

	private static final Logger log = LoggerFactory.getLogger(TicketService.class);
	private final EventRepository eventRepository;
	private final TicketTierRepository ticketTierRepository;
	private final TicketRepository ticketRepository;
	private final QrSigner qrSigner;
	private final Clock clock;
	private final TicketQueryRepository ticketQueryRepository;
	private final IdempotencyRepository idempotencyRepository;

	public TicketService(
		EventRepository eventRepository,
		TicketTierRepository ticketTierRepository,
		TicketRepository ticketRepository,
		QrSigner qrSigner,
		Clock clock,
		TicketQueryRepository ticketQueryRepository,
		IdempotencyRepository idempotencyRepository
	) {
		this.eventRepository = eventRepository;
		this.ticketTierRepository = ticketTierRepository;
		this.ticketRepository = ticketRepository;
		this.qrSigner = qrSigner;
		this.clock = clock;
		this.ticketQueryRepository = ticketQueryRepository;
		this.idempotencyRepository = idempotencyRepository;
	}

	@Transactional
	public Ticket buyTicket(UUID buyerId, UUID tierId, String idempotencyKey) {
		String normalizedKey = normalizeKey(idempotencyKey);

		// acquire idempotency lock
		boolean claimed = idempotencyRepository.tryClaim(buyerId, normalizedKey, clock.instant());
		if (!claimed) {
			UUID existingTicketId = idempotencyRepository.findTicketId(buyerId, normalizedKey)
				.orElseThrow(() -> new IllegalStateException("Idempotency key claimed without bound ticket"));

			var ticket = ticketRepository.findById(existingTicketId)
				.orElseThrow(AppException.TicketNotFound::new);

			log.atInfo()
				.setMessage("ticket.purchase.replayed")
				.addKeyValue("ticketId", existingTicketId)
				.addKeyValue("buyerId", buyerId)
				.log();

			return ticket;
		}

		// execute purchase
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

		var ticket = ticketRepository.save(Ticket.issue(buyerId, tierId, now));

		// bind ticket to idempotency table
		idempotencyRepository.bindTicket(buyerId, normalizedKey, ticket.id());

		log.atInfo()
			.setMessage("ticket.purchased")
			.addKeyValue("ticketId", ticket.id())
			.addKeyValue("tierId", tierId)
			.addKeyValue("buyerId", buyerId)
			.log();

		return ticket;
	}

	@Transactional(readOnly = true)
	public PagedResponse<TicketDto.Summary> listByBuyer(
		UUID buyerId,
		TicketDto.WalletFilter filter,
		PageQuery pageQuery
	) {
		return ticketQueryRepository
			.findByBuyer(buyerId, filter, clock.instant(), pageQuery);
	}

	public String issueQrToken(UUID buyerId, UUID ticketId) {
		var ticket = ticketRepository.findById(ticketId)
			.orElseThrow(AppException.TicketNotFound::new);

		ticket.ensureOwnedBy(buyerId);
		return qrSigner.sign(ticket.id());
	}

	private static String normalizeKey(String rawKey) {
		if (rawKey == null || rawKey.isBlank()) {
			throw new AppException.InvalidIdempotencyKey("key is blank");
		}
		String trimmed = rawKey.trim();
		if (trimmed.length() > 128) {
			throw new AppException.InvalidIdempotencyKey("exceeds 128 characters");
		}
		return trimmed;
	}
}