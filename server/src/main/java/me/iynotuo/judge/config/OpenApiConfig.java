package me.iynotuo.judge.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenAPI metadata and the shared error envelope used by the frontend's generated types.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "iOJ API",
                version = "1.0.0",
                description = "iOJ web API used by the frontend client."))
public class OpenApiConfig {

    @Bean
    public OpenApiCustomizer stableServerCustomizer() {
        return openApi -> openApi.setServers(List.of());
    }

    /**
     * Springdoc does not infer that record components are non-null. Mark all response DTO properties
     * as required; nullable properties still carry a {@code null} type in OpenAPI 3.1, so
     * openapi-typescript generates {@code T | null} instead of an optional property.
     */
    @Bean
    public OpenApiCustomizer requiredResponsePropertiesCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }
            openApi.getComponents().getSchemas().forEach((name, rawSchema) -> {
                Schema<?> schema = rawSchema;
                if (!isResponseSchema(name) || schema.getProperties() == null) {
                    return;
                }
                schema.setRequired(new ArrayList<>(schema.getProperties().keySet()));
            });
        };
    }

    private static boolean isResponseSchema(String name) {
        return name.endsWith("VO")
                || name.startsWith("ApiResponse")
                || name.startsWith("PageResult")
                || name.endsWith("Row");
    }

    @Bean
    public OperationCustomizer errorResponsesCustomizer() {
        return (operation, handlerMethod) -> {
            addDefaultErrorResponse(operation);
            return operation;
        };
    }

    private static void addDefaultErrorResponse(Operation operation) {
        if (operation.getResponses() == null) {
            operation.setResponses(new ApiResponses());
        }
        operation.getResponses().putIfAbsent("default",
                new io.swagger.v3.oas.models.responses.ApiResponse()
                        .description("统一错误响应")
                        .content(new Content().addMediaType(
                                org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                                new MediaType().schema(errorSchema()))));
    }

    private static Schema<?> errorSchema() {
        ObjectSchema schema = new ObjectSchema();
        schema.addProperty("code", new IntegerSchema().format("int32"));
        schema.addProperty("message", new StringSchema());
        schema.setRequired(List.of("code", "message"));
        return schema;
    }
}
