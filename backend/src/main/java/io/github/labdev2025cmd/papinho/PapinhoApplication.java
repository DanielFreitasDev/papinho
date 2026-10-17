package io.github.labdev2025cmd.papinho;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import io.github.labdev2025cmd.papinho.pki.PkiCommand;

/**
 * Ponto de entrada do Papinho: um único JAR que serve o SPA, a API e o WebSocket (ADR 0002).
 *
 * <p>O primeiro argumento {@code pki} desvia para o subcomando de linha de comando da PKI da LAN
 * ({@code java -jar papinho.jar pki init}) sem subir o contexto Spring nem o banco.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class PapinhoApplication {

    public static void main(String[] args) {
        if (args.length > 0 && PkiCommand.NAME.equals(args[0])) {
            // Em UTF-8 mesmo com locale POSIX (contêiner, systemd): senão os acentos saem como "?".
            int status = PkiCommand.run(Arrays.copyOfRange(args, 1, args.length),
                    new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8),
                    new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));
            System.exit(status);
        }
        SpringApplication.run(PapinhoApplication.class, args);
    }
}
