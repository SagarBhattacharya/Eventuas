package eventuas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private final UserProvisionFilter userProvisionFilter;

	public SecurityConfig(UserProvisionFilter userProvisionFilter) {
		this.userProvisionFilter = userProvisionFilter;
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
			.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(
					"/v3/api-docs/**",
					"/swagger-ui/**",
					"/swagger-ui.html",
					"/events/published",
					"/events/published/**"
				).permitAll()
				.anyRequest().authenticated()
			)
			.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
			.addFilterAfter(userProvisionFilter, BearerTokenAuthenticationFilter.class)
			.build();
	}
}