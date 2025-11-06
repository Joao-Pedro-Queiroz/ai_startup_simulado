package ai.startup.simulado;

import ai.startup.simulado.auth.JwtService;
import ai.startup.simulado.security.SecurityFilter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;


@SpringBootApplication
public class SimuladoApplication {
    public static void main(String[] args) {
        SpringApplication.run(SimuladoApplication.class, args);
    }

    @Bean
    public FilterRegistrationBean<SecurityFilter> securityFilter(JwtService jwtService) {
        FilterRegistrationBean<SecurityFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new SecurityFilter(jwtService)); // usa o que você já enviou
        reg.addUrlPatterns("/*");
        reg.setOrder(1);
        return reg;
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder b) {
        // Configurar timeouts maiores para chamadas ao serviço de modelo (Flask)
        // que pode demorar até 2 minutos para gerar todas as questões
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        factory.setReadTimeout((int) Duration.ofMinutes(5).toMillis());
        
        return b
            .requestFactory(() -> factory)
            .setConnectTimeout(Duration.ofSeconds(10))
            .setReadTimeout(Duration.ofMinutes(5))
            .build();
    }

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .components(new Components().addSecuritySchemes(
                "bearerAuth",
                new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
            ))
            // <— isto ativa o botão Authorize no topo da UI
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}