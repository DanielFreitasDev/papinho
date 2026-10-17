package io.github.labdev2025cmd.papinho.shared.error;

import java.io.IOException;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ErrorReportValve;

/**
 * Página de erro do próprio Tomcat (pedido recusado antes de chegar à aplicação, como {@code %00} no caminho):
 * Problem Details em vez do HTML com a versão do servidor (SG-01, SG-02).
 */
public class ProblemErrorReportValve extends ErrorReportValve {

    @Override
    protected void report(Request request, Response response, Throwable throwable) {
        int status = response.getStatus();
        if (status < 400 || response.getContentWritten() > 0 || !response.setErrorReported()) {
            return;
        }
        try {
            response.resetBuffer();
            ErrorResponses.write(response, status, "/");
        } catch (IOException | IllegalStateException ignored) {
            // Conexão já fechada: nada a responder.
        }
    }
}
