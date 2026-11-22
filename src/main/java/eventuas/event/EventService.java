package eventuas.event;

import eventuas.ticket_tier.TicketTierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class EventService {

	private final EventRepository eventRepository;
	private final EventAccess eventAccess;
	private final TicketTierRepository ticketTierRepository;

	public EventService(
		EventRepository eventRepository,
		EventAccess eventAccess,
		TicketTierRepository ticketTierRepository
	) {
		this.eventRepository = eventRepository;
		this.eventAccess = eventAccess;
		this.ticketTierRepository = ticketTierRepository;
	}

	public Event createDraft(UUID organizerId, CreateEventRequest request) {
		var event = Event.draft(
			request.name(),
			request.description(),
			request.venue(),
			request.startDate(),
			request.endDate(),
			request.salesStartDate(),
			request.salesEndDate(),
			organizerId
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
		return eventRepository.save(event.publish());
	}

	@Transactional
	public Event close(UUID organizerId, UUID eventId) {
		var event = eventAccess.requireOwned(organizerId, eventId, "close");
		var closedEvent = eventRepository.save(event.close());
		ticketTierRepository.closeAllActiveByEventId(eventId);
		return closedEvent;
	}
}