package eventuas.event;

import eventuas.ticket_tier.TicketTierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class EventService {

	private final EventRepository eventRepository;
	private final EventAccess eventAccess;
	private final TicketTierRepository ticketTierRepository;
	private final Clock clock;

	public EventService(
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
	public Event createDraft(
		UUID organizerId,
		String name,
		String description,
		String venue,
		Instant startDate,
		Instant endDate,
		Instant salesStartDate,
		Instant salesEndDate
	) {
		var event = Event.draft(
			name, description, venue,
			startDate, endDate, salesStartDate, salesEndDate,
			organizerId, clock.instant()
		);
		return eventRepository.save(event);
	}

	public List<Event> listByOrganizer(UUID organizerId) {
		return eventRepository.findByOrganizerIdOrderByCreatedAtDesc(organizerId);
	}

	public List<Event> listPublished() {
		return eventRepository.findAllPublished();
	}

	@Transactional
	public Event publish(UUID organizerId, UUID eventId) {
		var event = eventAccess.requireOwned(organizerId, eventId, "publish");
		return eventRepository.save(event.publish(clock.instant()));
	}

	@Transactional
	public Event close(UUID organizerId, UUID eventId) {
		var event = eventAccess.requireOwned(organizerId, eventId, "close");
		var closedEvent = eventRepository.save(event.close(clock.instant()));
		var _ = ticketTierRepository.closeAllActiveByEventId(eventId);
		return closedEvent;
	}
}