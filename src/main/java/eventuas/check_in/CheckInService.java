package eventuas.check_in;

import eventuas.errors.AppException;
import eventuas.event.EventAccess;
import eventuas.ticket.TicketRepository;
import eventuas.ticket_tier.TicketTierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class CheckInService {

	private final QrSigner qrSigner;
	private final TicketRepository ticketRepository;
	private final TicketTierRepository ticketTierRepository;
	private final EventAccess eventAccess;
	private final CheckInRepository checkInRepository;
	private final Clock clock;

	public CheckInService(
		QrSigner qrSigner,
		TicketRepository ticketRepository,
		TicketTierRepository ticketTierRepository,
		EventAccess eventAccess,
		CheckInRepository checkInRepository,
		Clock clock
	) {
		this.qrSigner = qrSigner;
		this.ticketRepository = ticketRepository;
		this.ticketTierRepository = ticketTierRepository;
		this.eventAccess = eventAccess;
		this.checkInRepository = checkInRepository;
		this.clock = clock;
	}

	@Transactional
	public CheckIn checkIn(UUID scannerId, String qrToken) {
		UUID ticketId = qrSigner.verifyAndExtractTicketId(qrToken);

		var ticket = ticketRepository.findById(ticketId)
			.orElseThrow(AppException.TicketNotFound::new);

		var tier = ticketTierRepository.findById(ticket.tierId())
			.orElseThrow(AppException.TierNotFound::new);

		var event = eventAccess.requireStaffOrOwner(
			scannerId, tier.eventId(), "check in tickets for"
		);
		event.ensureOpenForCheckIn();

		return checkInRepository.tryCheckIn(ticketId, scannerId, clock.instant())
			.orElseThrow(AppException.TicketAlreadyCheckedIn::new);
	}
}