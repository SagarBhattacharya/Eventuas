package eventuas.ticket;

import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

public interface TicketRepository extends CrudRepository<Ticket, UUID> {
	List<Ticket> findByPurchasedByOrderByCreatedAtDesc(UUID purchasedBy);
}