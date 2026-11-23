package eventuas.pagination;

import eventuas.event.Event;
import eventuas.event.EventDto;
import eventuas.ticket.TicketDto;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class PaginationRepository {

	private final JdbcClient jdbcClient;

	public PaginationRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public PagedResponse<Event> findPublishedEvents(EventDto.PublicFilter filter, PageQuery pageQuery) {
		if (filter.status() == Event.Status.DRAFT) {
			return PagedResponse.of(List.of(), pageQuery, 0L);
		}

		var where = new StringBuilder(" WHERE status <> 'DRAFT'");
		var params = new LinkedHashMap<String, Object>();
		appendEventPredicates(where, params, filter.q(), filter.status(), filter.from(), filter.to());
		return executeEventPage(where.toString(), params, " ORDER BY start_date ASC, id ASC", pageQuery);
	}

	public PagedResponse<Event> findOrganizerEvents(
		UUID organizerId,
		EventDto.OrganizerFilter filter,
		PageQuery pageQuery
	) {
		var where = new StringBuilder(" WHERE organizer_id = :organizerId");
		var params = new LinkedHashMap<String, Object>();
		params.put("organizerId", organizerId);
		appendEventPredicates(where, params, filter.q(), filter.status(), filter.from(), filter.to());
		return executeEventPage(where.toString(), params, " ORDER BY created_at DESC, id DESC", pageQuery);
	}

	public PagedResponse<TicketDto.Summary> findBuyerTickets(
		UUID buyerId,
		TicketDto.WalletFilter filter,
		Instant now,
		PageQuery pageQuery
	) {
		var baseFrom = """
			 FROM tickets t
			 JOIN ticket_tiers tt ON tt.id = t.tier_id
			 JOIN events e ON e.id = tt.event_id
			 LEFT JOIN check_ins ci ON ci.ticket_id = t.id
			 WHERE t.purchased_by = :buyerId
			""";
		var where = new StringBuilder(baseFrom);
		var params = new LinkedHashMap<String, Object>();
		params.put("buyerId", buyerId);

		if (filter.q() != null && !filter.q().isBlank()) {
			where.append(" AND (e.name ILIKE :q OR e.venue ILIKE :q)");
			params.put("q", "%" + filter.q().trim() + "%");
		}
		if (filter.checkedIn() != null) {
			where.append(filter.checkedIn() ? " AND ci.id IS NOT NULL" : " AND ci.id IS NULL");
		}
		if (filter.upcoming() != null) {
			where.append(filter.upcoming() ? " AND e.end_date >= :now" : " AND e.end_date < :now");
			params.put("now", Timestamp.from(now));
		}

		long total = jdbcClient.sql("SELECT COUNT(*)" + where)
			.params(params)
			.query(Long.class)
			.single();

		if (total == 0) {
			return PagedResponse.of(List.of(), pageQuery, 0L);
		}

		var dataParams = new LinkedHashMap<String, Object>(params);
		dataParams.put("limit", pageQuery.size());
		dataParams.put("offset", pageQuery.offset());

		List<TicketDto.Summary> items = jdbcClient.sql("""
			SELECT
				t.id AS ticket_id,
				e.id AS event_id,
				e.name AS event_name,
				e.venue AS venue,
				e.start_date AS start_date,
				e.end_date AS end_date,
				tt.tier AS tier_name,
				tt.price AS price,
				tt.currency AS currency,
				(ci.id IS NOT NULL) AS checked_in,
				ci.checked_in_at AS checked_in_at,
				t.created_at AS purchased_at
		""" + where + " ORDER BY t.created_at DESC, t.id DESC LIMIT :limit OFFSET :offset")
			.params(dataParams)
			.query(PaginationRepository::mapTicketSummary)
			.list();

		return PagedResponse.of(items, pageQuery, total);
	}

	private void appendEventPredicates(
		StringBuilder where,
		Map<String, Object> params,
		String q,
		Event.Status status,
		Instant from,
		Instant to
	) {
		if (q != null && !q.isBlank()) {
			where.append(" AND (name ILIKE :q OR description ILIKE :q OR venue ILIKE :q)");
			params.put("q", "%" + q.trim() + "%");
		}
		if (status != null) {
			where.append(" AND status = :status");
			params.put("status", status.name());
		}
		if (from != null) {
			where.append(" AND end_date >= :from");
			params.put("from", Timestamp.from(from));
		}
		if (to != null) {
			where.append(" AND start_date <= :to");
			params.put("to", Timestamp.from(to));
		}
	}

	private PagedResponse<Event> executeEventPage(
		String whereClause,
		Map<String, Object> params,
		String orderBy,
		PageQuery pageQuery
	) {
		long total = jdbcClient.sql("SELECT COUNT(*) FROM events" + whereClause)
			.params(params)
			.query(Long.class)
			.single();

		if (total == 0) {
			return PagedResponse.of(List.of(), pageQuery, 0L);
		}

		var dataParams = new LinkedHashMap<String, Object>(params);
		dataParams.put("limit", pageQuery.size());
		dataParams.put("offset", pageQuery.offset());

		List<Event> items = jdbcClient.sql("""
				SELECT id, name, description, venue, start_date, end_date,
				       sales_start_date, sales_end_date, organizer_id, status,
				       created_at, updated_at
				FROM events
				""" + whereClause + orderBy + " LIMIT :limit OFFSET :offset")
			.params(dataParams)
			.query(PaginationRepository::mapEvent)
			.list();

		return PagedResponse.of(items, pageQuery, total);
	}

	private static Event mapEvent(ResultSet rs, int rowNum) throws SQLException {
		return new Event(
			rs.getObject("id", UUID.class),
			rs.getString("name"),
			rs.getString("description"),
			rs.getString("venue"),
			rs.getTimestamp("start_date").toInstant(),
			rs.getTimestamp("end_date").toInstant(),
			toInstant(rs.getTimestamp("sales_start_date")),
			toInstant(rs.getTimestamp("sales_end_date")),
			rs.getObject("organizer_id", UUID.class),
			Event.Status.valueOf(rs.getString("status")),
			rs.getTimestamp("created_at").toInstant(),
			rs.getTimestamp("updated_at").toInstant()
		);
	}

	private static TicketDto.Summary mapTicketSummary(ResultSet rs, int rowNum) throws SQLException {
		return new TicketDto.Summary(
			rs.getObject("ticket_id", UUID.class),
			rs.getObject("event_id", UUID.class),
			rs.getString("event_name"),
			rs.getString("venue"),
			rs.getTimestamp("start_date").toInstant(),
			rs.getTimestamp("end_date").toInstant(),
			rs.getString("tier_name"),
			rs.getLong("price"),
			rs.getString("currency"),
			rs.getBoolean("checked_in"),
			toInstant(rs.getTimestamp("checked_in_at")),
			rs.getTimestamp("purchased_at").toInstant()
		);
	}

	private static Instant toInstant(Timestamp ts) {
		return ts == null ? null : ts.toInstant();
	}
}