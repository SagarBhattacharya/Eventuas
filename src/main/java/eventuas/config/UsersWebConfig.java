package eventuas.config;

import eventuas.user.CurrentUserArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class UsersWebConfig implements WebMvcConfigurer {

	@Override
	public void addArgumentResolvers(
		List<HandlerMethodArgumentResolver> resolvers
	) {
		resolvers.add(new CurrentUserArgumentResolver());
	}
}