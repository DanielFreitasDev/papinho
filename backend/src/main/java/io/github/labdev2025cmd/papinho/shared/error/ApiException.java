package io.github.labdev2025cmd.papinho.shared.error;

import java.net.URI;
import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * Base das exceções de negócio que viram Problem Details.
 *
 * <p>O {@code slug} identifica o problema ({@code urn:papinho:problem:<slug>}) e as chaves de mensagem
 * {@code problem.<slug>.title} e {@code problem.<slug>.detail}; os argumentos preenchem o detalhe.
 * A mensagem da exceção é técnica, em inglês, e nunca é enviada ao cliente.</p>
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String slug;
    private final Object[] detailArguments;
    private final Map<String, Object> properties;

    protected ApiException(HttpStatus status, String slug, String technicalMessage, Object... detailArguments) {
        this(status, slug, Map.of(), technicalMessage, detailArguments);
    }

    protected ApiException(HttpStatus status, String slug, Map<String, Object> properties, String technicalMessage,
            Object... detailArguments) {
        super(technicalMessage);
        this.status = status;
        this.slug = slug;
        this.detailArguments = detailArguments.clone();
        this.properties = Map.copyOf(properties);
    }

    public HttpStatus status() {
        return status;
    }

    public String slug() {
        return slug;
    }

    public URI type() {
        return URI.create("urn:papinho:problem:" + slug);
    }

    public Object[] detailArguments() {
        return detailArguments.clone();
    }

    /** Propriedades extras do Problem Details (por exemplo, {@code retryAfterSeconds}). */
    public Map<String, Object> properties() {
        return properties;
    }

    /** Cabeçalhos HTTP que acompanham a resposta (por exemplo, {@code Content-Range} no 416). */
    public Map<String, String> headers() {
        return Map.of();
    }
}
