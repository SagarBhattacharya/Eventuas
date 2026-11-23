package eventuas.pagination;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageQueryTest {

	@Test
	void clampsNegativePageAndOutOfBoundsSize() {
		var defaults = new PageQuery(null, null);
		assertThat(defaults.page()).isEqualTo(0);
		assertThat(defaults.size()).isEqualTo(20);
		assertThat(defaults.offset()).isEqualTo(0L);

		var clamped = new PageQuery(-5, 500);
		assertThat(clamped.page()).isEqualTo(0);
		assertThat(clamped.size()).isEqualTo(100);

		var zeroSize = new PageQuery(2, 0);
		assertThat(zeroSize.page()).isEqualTo(2);
		assertThat(zeroSize.size()).isEqualTo(20);
		assertThat(zeroSize.offset()).isEqualTo(40L);
	}

	@Test
	void computesTotalPagesAndHasNextAccurately() {
		var empty = PagedResponse.of(List.of(), PageQuery.of(0, 10), 0);
		assertThat(empty.totalPages()).isEqualTo(0);
		assertThat(empty.hasNext()).isFalse();

		var exactMultiple = PagedResponse.of(List.of("a", "b"), PageQuery.of(1, 2), 4);
		assertThat(exactMultiple.totalPages()).isEqualTo(2);
		assertThat(exactMultiple.hasNext()).isFalse();

		var partialLastPage = PagedResponse.of(List.of("a", "b"), PageQuery.of(0, 2), 5);
		assertThat(partialLastPage.totalPages()).isEqualTo(3);
		assertThat(partialLastPage.hasNext()).isTrue();
	}

	@Test
	void mapsItemsWhilePreservingPageMetadata() {
		var original = PagedResponse.of(List.of("rust", "java"), PageQuery.of(1, 2), 5);
		var mapped = original.map(String::toUpperCase);

		assertThat(mapped.items()).containsExactly("RUST", "JAVA");
		assertThat(mapped.page()).isEqualTo(1);
		assertThat(mapped.size()).isEqualTo(2);
		assertThat(mapped.totalElements()).isEqualTo(5);
		assertThat(mapped.totalPages()).isEqualTo(3);
		assertThat(mapped.hasNext()).isTrue();
	}
}