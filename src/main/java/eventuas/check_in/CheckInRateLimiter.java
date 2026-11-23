package eventuas.check_in;

import eventuas.errors.AppException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CheckInRateLimiter {

	private static final Duration REFILL_PERIOD = Duration.ofMinutes(1);
	private static final int MAX_TRACKED_SCANNERS = 10_000;

	private static final class Bucket {
		double tokens;
		Instant lastRefill;

		Bucket(double tokens, Instant lastRefill) {
			this.tokens = tokens;
			this.lastRefill = lastRefill;
		}
	}

	private final Clock clock;
	private final int maxRequestsPerMinute;
	private final ConcurrentHashMap<UUID, Bucket> buckets = new ConcurrentHashMap<>();

	public CheckInRateLimiter(
		Clock clock,
		@Value("${app.check-in.rate-limit-per-minute:60}") int maxRequestsPerMinute
	) {
		if (maxRequestsPerMinute < 1) {
			throw new IllegalArgumentException("app.check-in.rate-limit-per-minute must be >= 1");
		}
		this.clock = clock;
		this.maxRequestsPerMinute = maxRequestsPerMinute;
	}

	public void acquire(UUID scannerId) {
		Instant now = clock.instant();
		if (buckets.size() >= MAX_TRACKED_SCANNERS && !buckets.containsKey(scannerId)) {
			buckets.clear();
		}

		Bucket bucket = buckets.computeIfAbsent(scannerId, _ -> new Bucket(maxRequestsPerMinute, now));
		synchronized (bucket) {
			long elapsedMillis = Math.max(0L, Duration.between(bucket.lastRefill, now).toMillis());
			if (elapsedMillis > 0) {
				double refill = (elapsedMillis / (double) REFILL_PERIOD.toMillis()) * maxRequestsPerMinute;
				bucket.tokens = Math.min(maxRequestsPerMinute, bucket.tokens + refill);
				bucket.lastRefill = now;
			}

			if (bucket.tokens < 1.0) {
				throw new AppException.RateLimitExceeded();
			}
			bucket.tokens -= 1.0;
		}
	}
}