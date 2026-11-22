package eventuas.check_in;

import eventuas.errors.AppException;
import eventuas.event.EventAccess;
import eventuas.event.EventStatus;
import eventuas.ticket.TicketRepository;
import eventuas.ticket_tier.TicketTierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CheckInService {

	private final QrSigner qrSigner;
	private final TicketRepository ticketRepository;
	private final TicketTierRepository ticketTierRepository;
	private final EventAccess eventAccess;
	private final CheckInRepository checkInRepository;

	public CheckInService(
		QrSigner qrSigner,
		TicketRepository ticketRepository,
		TicketTierRepository ticketTierRepository,
		EventAccess eventAccess,
		CheckInRepository checkInRepository
	) {
		this.qrSigner = qrSigner;
		this.ticketRepository = ticketRepository;
		this.ticketTierRepository = ticketTierRepository;
		this.eventAccess = eventAccess;
		this.checkInRepository = checkInRepository;
	}

	@Transactional
	public CheckIn checkIn(UUID scannerId, String qrToken) {
		UUID ticketId = qrSigner.verifyAndExtractTicketId(qrToken);

		var ticket = ticketRepository.findById(ticketId)
			.orElseThrow(AppException.TicketNotFound::new);

		var tier = ticketTierRepository.findById(ticket.tierId())
			.orElseThrow(AppException.TierNotFound::new);

		var event = eventAccess.requireStaffOrOwner(scannerId, tier.eventId(), "check in tickets for");

		if (event.status() != EventStatus.ACTIVE) {
			throw new AppException.EventNotActive();
		}

		return checkInRepository.tryCheckIn(ticketId, scannerId)
			.orElseThrow(AppException.TicketAlreadyCheckedIn::new);
	}
}