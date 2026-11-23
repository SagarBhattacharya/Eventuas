package eventuas.check_in;

import eventuas.errors.AppException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckInRateLimiterTest {

	private static final class MutableClock extends Clock {
		private Instant current;

		MutableClock(Instant initial) {
			this.current = initial;
		}

		void advanceSeconds(long seconds) {
			this.current = this.current.plusSeconds(seconds);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return current;
		}
	}

	@Test
	void enforcesLimitPerScannerAndRefillsOverTime() {
		var clock = new MutableClock(Instant.parse("2026-11-23T10:00:00Z"));
		var limiter = new CheckInRateLimiter(clock, 2);
		UUID scannerA = UUID.randomUUID();
		UUID scannerB = UUID.randomUUID();

		// Scanner A consumes both tokens
		assertThatCode(() -> limiter.acquire(scannerA)).doesNotThrowAnyException();
		assertThatCode(() -> limiter.acquire(scannerA)).doesNotThrowAnyException();
		assertThatThrownBy(() -> limiter.acquire(scannerA))
			.isInstanceOf(AppException.RateLimitExceeded.class);

		// Scanner B has an independent bucket
		assertThatCode(() -> limiter.acquire(scannerB)).doesNotThrowAnyException();

		// Advancing 30s refills 1 token (rate = 2/min)
		clock.advanceSeconds(30);
		assertThatCode(() -> limiter.acquire(scannerA)).doesNotThrowAnyException();
		assertThatThrownBy(() -> limiter.acquire(scannerA))
			.isInstanceOf(AppException.RateLimitExceeded.class);
	}
}