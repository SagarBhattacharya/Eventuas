package eventuas.pagination;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SqlFilter {

	private final List<String> clauses = new ArrayList<>();
	private final Map<String, Object> params = new LinkedHashMap<>();

	public SqlFilter and(String clause) {
		clauses.add(clause);
		return this;
	}

	public SqlFilter and(String clause, String paramName, Object value) {
		clauses.add(clause);
		params.put(paramName, value);
		return this;
	}

	public SqlFilter andIfPresent(String clause, String paramName, Object value) {
		if (value != null) {
			and(clause, paramName, value);
		}
		return this;
	}

	public SqlFilter andLikeIfPresent(String clause, String paramName, String rawQuery) {
		if (rawQuery != null && !rawQuery.isBlank()) {
			and(clause, paramName, escapeLikePattern(rawQuery));
		}
		return this;
	}

	public String whereClause() {
		return clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
	}

	public Map<String, Object> params() {
		return Map.copyOf(params);
	}

	public Map<String, Object> paramsWithPage(PageQuery pageQuery) {
		var paged = new LinkedHashMap<String, Object>(params);
		paged.put("limit", pageQuery.size());
		paged.put("offset", pageQuery.offset());
		return paged;
	}

	private static String escapeLikePattern(String raw) {
		String escaped = raw.trim()
			.replace("\\", "\\\\")
			.replace("%", "\\%")
			.replace("_", "\\_");
		return "%" + escaped + "%";
	}
}