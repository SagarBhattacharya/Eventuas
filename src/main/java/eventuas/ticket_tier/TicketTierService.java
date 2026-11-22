package eventuas.ticket_tier;

import eventuas.errors.AppException;
import eventuas.event.Event;
import eventuas.event.EventAccess;
import eventuas.event.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class TicketTierService {

	private final EventRepository eventRepository;
	private final EventAccess eventAccess;
	private final TicketTierRepository ticketTierRepository;
	private final Clock clock;

	public TicketTierService(
		EventRepository eventRepository,
		EventAccess eventAccess,
		TicketTierRepository ticketTierRepository,
		Clock clock
	) {
		this.eventRepository = eventRepository;
		this.eventAccess = eventAccess;
		this.ticketTierRepository = ticketTierRepository;
		this.clock = clock;
	}

	@Transactional
	public TicketTier createTier(
		UUID organizerId,
		UUID eventId,
		String tier,
		String description,
		long availableQty,
		long price,
		String currency
	) {
		var event = eventAccess.requireOwned(organizerId, eventId, "create tiers for");
		event.ensureAllowsTierManagement();

		var ticketTier = TicketTier.create(
			tier,
			description,
			availableQty,
			price,
			currency,
			eventId,
			clock.instant()
		);
		return ticketTierRepository.save(ticketTier);
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

		return ticketTierRepository.save(tier.close(clock.instant()));
	}

	public List<TicketTier> listPublicTiers(UUID eventId) {
		var event = eventRepository.findById(eventId)
			.orElseThrow(AppException.EventNotFound::new);

		event.ensureOpenToPublic();
		return ticketTierRepository.findActiveByEventId(eventId);
	}
}