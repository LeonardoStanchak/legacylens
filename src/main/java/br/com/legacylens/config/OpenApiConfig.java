package br.com.legacylens.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI legacyLensOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("LegacyLens API")
                        .description("""
                                Ferramenta de análise automatizada de projetos Java legados.
                                Gera UML, Excel comparativo e README técnico.
                                Desenvolvido em Java 17 + Spring Boot 3.5.3
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Leonardo Stanchak")
                                .email("leonardostanchak01@hotmail.com")
                                .url("https://www.linkedin.com/in/leonardo-stanchak-21b9141a4/"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT"))
                );
    }
}
