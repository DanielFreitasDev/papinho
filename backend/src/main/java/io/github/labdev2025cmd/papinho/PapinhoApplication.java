package io.github.labdev2025cmd.papinho;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Ponto de entrada do Papinho: um único JAR que serve o SPA, a API e o WebSocket (ADR 0002).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class PapinhoApplication {

    public static void main(String[] args) {
        SpringApplication.run(PapinhoApplication.class, args);
    }
}
