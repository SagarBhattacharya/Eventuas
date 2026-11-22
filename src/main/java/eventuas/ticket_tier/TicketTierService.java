package eventuas.ticket_tier;

import eventuas.errors.AppException;
import eventuas.event.EventAccess;
import eventuas.event.EventRepository;
import eventuas.event.EventStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TicketTierService {

	private final EventRepository eventRepository;
	private final EventAccess eventAccess;
	private final TicketTierRepository ticketTierRepository;

	public TicketTierService(
		EventRepository eventRepository,
		EventAccess eventAccess,
		TicketTierRepository ticketTierRepository
	) {
		this.eventRepository = eventRepository;
		this.eventAccess = eventAccess;
		this.ticketTierRepository = ticketTierRepository;
	}

	@Transactional
	public TicketTier createTier(UUID organizerId, UUID eventId, CreateTicketTierRequest request) {
		var event = eventAccess.requireOwned(organizerId, eventId, "create tiers for");
		if (event.status() == EventStatus.CLOSED) {
			throw new AppException.EventAlreadyClosed();
		}

		var ticket = TicketTier.create(
			request.tier().trim(),
			request.description(),
			request.availableQty(),
			request.price(),
			request.currency(),
			eventId
		);
		return ticketTierRepository.save(ticket);
	}

	public List<TicketTier> listByOwnedEvent(UUID organizerId, UUID eventId) {
		var _ = eventAccess.requireOwned(organizerId, eventId, "lookup tiers for");
		return ticketTierRepository.findByEventIdOrderByCreatedAtAsc(eventId);
	}

	@Transactional
	public TicketTier closeTier(UUID organizerId, UUID eventId, UUID tierId) {
		var _ = eventAccess.requireOwned(organizerId, eventId, "close this tier for");
		var tier = ticketTierRepository.findByIdAndEventId(tierId, eventId)
			.orElseThrow(AppException.TierNotFound::new);

		var newTier = tier.close();
		return ticketTierRepository.save(newTier);
	}

	public List<TicketTier> listPublicTiers(UUID eventId) {
		var event = eventRepository.findById(eventId)
			.orElseThrow(AppException.EventNotFound::new);

		if (event.status() == EventStatus.DRAFT) {
			throw new AppException.EventNotFound();
		}

		return ticketTierRepository.findActiveByEventId(eventId);
	}
}