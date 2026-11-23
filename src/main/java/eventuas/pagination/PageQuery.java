package eventuas.pagination;

import io.swagger.v3.oas.annotations.media.Schema;

public record PageQuery(
	@Schema(description = "Zero-based page index", defaultValue = "0", minimum = "0")
	Integer page,

	@Schema(description = "Page size (1 to 100)", defaultValue = "20", minimum = "1", maximum = "100")
	Integer size
) {
	public static final int DEFAULT_PAGE = 0;
	public static final int DEFAULT_SIZE = 20;
	public static final int MAX_SIZE = 100;

	public PageQuery {
		page = (page == null || page < 0) ? DEFAULT_PAGE : page;
		if (size == null || size <= 0) {
			size = DEFAULT_SIZE;
		} else if (size > MAX_SIZE) {
			size = MAX_SIZE;
		}
	}

	public static PageQuery of(int page, int size) {
		return new PageQuery(page, size);
	}

	public long offset() {
		return (long) page * size;
	}
}