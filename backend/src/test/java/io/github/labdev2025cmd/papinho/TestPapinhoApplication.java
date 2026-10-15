package io.github.labdev2025cmd.papinho;

import org.springframework.boot.SpringApplication;

/** Sobe a aplicação localmente com o PostgreSQL do Testcontainers, sem precisar do compose.yaml. */
public class TestPapinhoApplication {

    public static void main(String[] args) {
        SpringApplication.from(PapinhoApplication::main).with(TestcontainersConfiguration.class).run(args);
    }
}
