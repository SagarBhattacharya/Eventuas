package eventuas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jdbc.repository.config.EnableJdbcAuditing;

import java.time.Clock;

@SpringBootApplication
@EnableJdbcAuditing
public class EventuasApplication {
	static void main(String[] args) {
		SpringApplication.run(EventuasApplication.class, args);
	}

	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}
}
