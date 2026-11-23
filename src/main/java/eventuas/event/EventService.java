package eventuas.event;

import eventuas.errors.AppException;
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
@Transactional(readOnly = true)
public class EventService {

	private final EventRepository eventRepository;
	private final EventAccess eventAccess;
	private final TicketTierRepository ticketTierRepository;
	private final Clock clock;
	private final PaginationRepository paginationRepository;

	public EventService(
		EventRepository eventRepository,
		EventAccess eventAccess,
		TicketTierRepository ticketTierRepository,
		Clock clock,
		PaginationRepository eventQueryRepository
	) {
		this.eventRepository = eventRepository;
		this.eventAccess = eventAccess;
		this.ticketTierRepository = ticketTierRepository;
		this.clock = clock;
		this.paginationRepository = eventQueryRepository;
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

	@Transactional(readOnly = true)
	public PagedResponse<EventDto.Response> listByOrganizer(
		UUID organizerId,
		EventDto.OrganizerFilter filter,
		PageQuery pageQuery
	) {
		return paginationRepository
			.findOrganizerEvents(organizerId, filter, pageQuery)
			.map(EventDto.Response::from);
	}

	@Transactional(readOnly = true)
	public PagedResponse<EventDto.Response> listPublished(EventDto.PublicFilter filter, PageQuery pageQuery) {
		return paginationRepository.findPublishedEvents(filter, pageQuery)
			.map(EventDto.Response::from);
	}

	@Transactional(readOnly = true)
	public EventDto.PublicDetail getPublishedDetail(UUID eventId) {
		var event = eventRepository.findById(eventId)
			.orElseThrow(AppException.EventNotFound::new);
		event.ensureOpenToPublic();

		var tiers = ticketTierRepository.findActiveByEventId(eventId);
		return EventDto.PublicDetail.from(event, tiers);
	}

	@Transactional(readOnly = true)
	public EventDto.Stats getStats(UUID organizerId, UUID eventId) {
		var event = eventAccess.requireOwned(organizerId, eventId, "view stats for");
		var tierStats = ticketTierRepository.findStatsByEventId(eventId);
		return EventDto.Stats.from(event, tierStats);
	}

	@Transactional
	public Event publish(UUID organizerId, UUID eventId) {
		var event = eventAccess.requireOwned(organizerId, eventId, "publish");
		long activeTiers = ticketTierRepository.countActiveByEventId(eventId);
		return eventRepository.save(event.publish(activeTiers, clock.instant()));
	}

	@Transactional
	public Event close(UUID organizerId, UUID eventId) {
		var event = eventAccess.requireOwned(organizerId, eventId, "close");
		var closedEvent = eventRepository.save(event.close(clock.instant()));
		var _ = ticketTierRepository.closeAllActiveByEventId(eventId);
		return closedEvent;
	}
}