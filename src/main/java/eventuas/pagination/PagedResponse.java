package eventuas.pagination;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.function.Function;

@Schema(name = "PagedResponse")
public record PagedResponse<T>(
	List<T> items,
	int page,
	int size,
	long totalElements,
	int totalPages,
	boolean hasNext
) {
	public static <T> PagedResponse<T> of(List<T> items, PageQuery query, long totalElements) {
		int size = query.size();
		int page = query.page();
		int totalPages = totalElements == 0
			? 0
			: (int) Math.ceil((double) totalElements / size);

		return new PagedResponse<>(
			items,
			page,
			size,
			totalElements,
			totalPages,
			(page + 1) < totalPages
		);
	}

	public <U> PagedResponse<U> map(Function<? super T, ? extends U> mapper) {
		List<U> mapped = items.stream().<U>map(mapper).toList();
		return new PagedResponse<>(mapped, page, size, totalElements, totalPages, hasNext);
	}
}