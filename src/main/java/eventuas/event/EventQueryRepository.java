package eventuas.event;

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
public class EventQueryRepository {

	private static final RowMapper<Event> EVENT_MAPPER =
		DataClassRowMapper.newInstance(Event.class);

	private final JdbcClient jdbcClient;

	EventQueryRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	PagedResponse<Event> findPublished(EventDto.PublicFilter filter, PageQuery pageQuery) {
		if (filter.status() == Event.Status.DRAFT) {
			return PagedResponse.of(List.of(), pageQuery, 0L);
		}

		var sqlFilter = new SqlFilter().and("status <> 'DRAFT'");
		appendCommonPredicates(sqlFilter, filter.q(), filter.status(), filter.from(), filter.to());
		return executePage(sqlFilter, " ORDER BY start_date ASC, id ASC", pageQuery);
	}

	PagedResponse<Event> findByOrganizer(
		UUID organizerId,
		EventDto.OrganizerFilter filter,
		PageQuery pageQuery
	) {
		var sqlFilter = new SqlFilter().and("organizer_id = :organizerId", "organizerId", organizerId);
		appendCommonPredicates(sqlFilter, filter.q(), filter.status(), filter.from(), filter.to());
		return executePage(sqlFilter, " ORDER BY created_at DESC, id DESC", pageQuery);
	}

	private static void appendCommonPredicates(
		SqlFilter sqlFilter,
		String q,
		Event.Status status,
		Instant from,
		Instant to
	) {
		sqlFilter
			.andLikeIfPresent(
				"(name ILIKE :q ESCAPE '\\' OR description ILIKE :q ESCAPE '\\' OR venue ILIKE :q ESCAPE '\\')",
				"q",
				q
			)
			.andIfPresent("status = :status", "status", status != null ? status.name() : null)
			.andIfPresent("end_date >= :from", "from", from != null ? Timestamp.from(from) : null)
			.andIfPresent("start_date <= :to", "to", to != null ? Timestamp.from(to) : null);
	}

	private PagedResponse<Event> executePage(
		SqlFilter filter,
		String orderBy,
		PageQuery pageQuery
	) {
		String where = filter.whereClause();

		long total = jdbcClient.sql("SELECT COUNT(*) FROM events" + where)
			.params(filter.params())
			.query(Long.class)
			.single();

		if (total == 0) {
			return PagedResponse.of(List.of(), pageQuery, 0L);
		}

		List<Event> items = jdbcClient.sql("""
				SELECT id, name, description, venue, start_date, end_date,
				       sales_start_date, sales_end_date, organizer_id, status,
				       created_at, updated_at
				FROM events
				""" + where + orderBy + " LIMIT :limit OFFSET :offset")
			.params(filter.paramsWithPage(pageQuery))
			.query(EVENT_MAPPER)
			.list();

		return PagedResponse.of(items, pageQuery, total);
	}
}