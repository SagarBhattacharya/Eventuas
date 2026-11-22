package eventuas.config;

import eventuas.errors.ApiErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.Jwt;

@Configuration
public class OpenApiConfig {

	static {
		SpringDocUtils.getConfig().addRequestWrapperToIgnore(Jwt.class);
	}

	@Bean
	public OpenAPI eventuasOpenApi() {
		final String schemeName = "bearerAuth";

		return new OpenAPI()
			.info(new Info()
				.title("Eventuas API")
				.version("v1")
				.description("Event management, ticketing, staff delegation, and gate check-in API"))
			.addSecurityItem(new SecurityRequirement().addList(schemeName))
			.components(new Components()
				.addSecuritySchemes(schemeName, new SecurityScheme()
					.name(schemeName)
					.type(SecurityScheme.Type.HTTP)
					.scheme("bearer")
					.bearerFormat("JWT")
				)
			);
	}

	@Bean
	public OpenApiCustomizer errorSchemaCustomizer() {
		return openApi -> {
			var resolved = ModelConverters.getInstance()
				.readAllAsResolvedSchema(ApiErrorResponse.class);

			resolved.referencedSchemas.forEach((name, schema) ->
				openApi.getComponents().addSchemas(name, schema));
			openApi.getComponents().addSchemas("ApiError", resolved.schema);

			var errorMediaType = new MediaType()
				.schema(new Schema<>().$ref("#/components/schemas/ApiError"));
			var content = new Content().addMediaType("application/problem+json", errorMediaType);

			openApi.getComponents().addResponses("ProblemResponse",
				new ApiResponse()
					.description("Domain or validation error")
					.content(content));
		};
	}
}