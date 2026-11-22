package eventuas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jdbc.repository.config.EnableJdbcAuditing;

@SpringBootApplication
@EnableJdbcAuditing
public class EventuasApplication {
	static void main(String[] args) {
		SpringApplication.run(EventuasApplication.class, args);
	}
}
