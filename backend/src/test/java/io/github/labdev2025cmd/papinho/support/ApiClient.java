package io.github.labdev2025cmd.papinho.support;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * "Navegador" de teste sobre o MockMvc: guarda os cookies entre requisições (sessão {@code __Host-SESSION} e
 * {@code XSRF-TOKEN}), manda o cabeçalho CSRF nas escritas como o SPA e usa um IP próprio, para que os limites
 * de tentativa de um teste não afetem outro.
 */
public final class ApiClient {

    private static final AtomicInteger IP_SEQUENCE = new AtomicInteger(1);

    private final MockMvcTester mvc;
    private final JsonMapper json;
    private final Map<String, String> cookies = new LinkedHashMap<>();
    private final String remoteAddress;

    public ApiClient(MockMvcTester mvc, JsonMapper json) {
        this.mvc = mvc;
        this.json = json;
        int n = IP_SEQUENCE.getAndIncrement();
        this.remoteAddress = "10." + ((n >> 16) & 0xFF) + "." + ((n >> 8) & 0xFF) + "." + (n & 0xFF);
    }

    public MvcTestResult get(String uri) {
        return send(MockMvcRequestBuilders.get(uri), null);
    }

    public MvcTestResult post(String uri, @Nullable Object body) {
        return send(MockMvcRequestBuilders.post(uri), body);
    }

    public MvcTestResult put(String uri, @Nullable Object body) {
        return send(MockMvcRequestBuilders.put(uri), body);
    }

    public MvcTestResult patch(String uri, @Nullable Object body) {
        return send(MockMvcRequestBuilders.patch(uri), body);
    }

    public MvcTestResult delete(String uri, @Nullable Object body) {
        return send(MockMvcRequestBuilders.delete(uri), body);
    }

    public MvcTestResult multipart(HttpMethod method, String uri, MockMultipartFile file) {
        MockMultipartHttpServletRequestBuilder builder = MockMvcRequestBuilders.multipart(method, uri).file(file);
        return send(builder, null);
    }

    /** Lê o corpo JSON da resposta. */
    public JsonNode json(MvcTestResult result) {
        return json.readTree(result.getResponse().getContentAsByteArray());
    }

    /** Corpo da resposta como texto UTF-8 (mensagens de falha dos testes). */
    public static String text(MvcTestResult result) {
        return new String(result.getResponse().getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    }

    public @Nullable String cookie(String name) {
        return cookies.get(name);
    }

    /** Esquece a sessão (como fechar o navegador), mantendo o IP. */
    public void clearCookies() {
        cookies.clear();
    }

    /** Garante o cookie CSRF, como o SPA faz ao abrir. */
    public ApiClient bootstrap() {
        get("/api/v1/instance");
        return this;
    }

    private MvcTestResult send(AbstractMockHttpServletRequestBuilder<?> builder, @Nullable Object body) {
        builder.with(request -> {
            request.setRemoteAddr(remoteAddress);
            request.setSecure(true);
            return request;
        });
        if (!cookies.isEmpty()) {
            builder.cookie(cookies.entrySet().stream().map(e -> new Cookie(e.getKey(), e.getValue()))
                    .toArray(Cookie[]::new));
        }
        String csrf = cookies.get("XSRF-TOKEN");
        if (csrf != null) {
            builder.header("X-XSRF-TOKEN", csrf);
        }
        builder.header(HttpHeaders.ACCEPT_LANGUAGE, "pt-BR");
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        }
        MvcTestResult result = mvc.perform(builder);
        for (Cookie cookie : result.getResponse().getCookies()) {
            if (cookie.getMaxAge() == 0 || cookie.getValue() == null || cookie.getValue().isEmpty()) {
                cookies.remove(cookie.getName());
            } else {
                cookies.put(cookie.getName(), cookie.getValue());
            }
        }
        for (String header : result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)) {
            String[] pair = header.split(";", 2)[0].split("=", 2);
            if (pair.length == 2) {
                if (pair[1].isEmpty() || header.contains("Max-Age=0")) {
                    cookies.remove(pair[0]);
                } else {
                    cookies.put(pair[0], pair[1]);
                }
            }
        }
        return result;
    }
}
