package eventuas.ticket;

import eventuas.pagination.PageQuery;
import eventuas.pagination.PagedResponse;
import eventuas.pagination.SqlFilter;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
@SuppressWarnings("SqlSourceToSinkFlow")
public class TicketQueryRepository {

	private static final RowMapper<TicketDto.Summary> SUMMARY_MAPPER =
		DataClassRowMapper.newInstance(TicketDto.Summary.class);

	private static final String FROM_JOIN = """
		 FROM tickets t
		 JOIN ticket_tiers tt ON tt.id = t.tier_id
		 JOIN events e ON e.id = tt.event_id
		 LEFT JOIN check_ins ci ON ci.ticket_id = t.id
		""";

	private final JdbcClient jdbcClient;

	TicketQueryRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	PagedResponse<TicketDto.Summary> findByBuyer(
		UUID buyerId,
		TicketDto.WalletFilter filter,
		Instant now,
		PageQuery pageQuery
	) {
		var sqlFilter = new SqlFilter()
			.and("t.purchased_by = :buyerId", "buyerId", buyerId)
			.andLikeIfPresent("(e.name ILIKE :q ESCAPE '\\' OR e.venue ILIKE :q ESCAPE '\\')", "q", filter.q());

		if (filter.checkedIn() != null) {
			sqlFilter.and(filter.checkedIn() ? "ci.id IS NOT NULL" : "ci.id IS NULL");
		}
		if (filter.upcoming() != null) {
			sqlFilter.and(
				filter.upcoming() ? "e.end_date >= :now" : "e.end_date < :now",
				"now",
				Timestamp.from(now)
			);
		}

		String where = sqlFilter.whereClause();

		long total = jdbcClient.sql("SELECT COUNT(*)" + FROM_JOIN + where)
			.params(sqlFilter.params())
			.query(Long.class)
			.single();

		if (total == 0) {
			return PagedResponse.of(List.of(), pageQuery, 0L);
		}

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
				""" + FROM_JOIN + where + " ORDER BY t.created_at DESC, t.id DESC LIMIT :limit OFFSET :offset")
			.params(sqlFilter.paramsWithPage(pageQuery))
			.query(SUMMARY_MAPPER)
			.list();

		return PagedResponse.of(items, pageQuery, total);
	}
}