package io.github.labdev2025cmd.papinho.shared.web;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Dados do cliente para a tela de sessões (ID-06): descrição curta do dispositivo e IP aproximado.
 * O IP completo nunca é guardado: IPv4 perde o último octeto e IPv6 fica só com o prefixo /48.
 */
public record ClientInfo(String device, String approximateIp) {

    public static ClientInfo from(HttpServletRequest request) {
        return new ClientInfo(describeDevice(request.getHeader("User-Agent")), approximate(request.getRemoteAddr()));
    }

    static String describeDevice(@Nullable String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Dispositivo desconhecido";
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        String browser = ua.contains("edg/") ? "Edge"
                : ua.contains("firefox/") ? "Firefox"
                : ua.contains("chrome/") || ua.contains("crios/") ? "Chrome"
                : ua.contains("safari/") ? "Safari"
                : "Navegador";
        String system = ua.contains("android") ? "Android"
                : ua.contains("iphone") || ua.contains("ipad") ? "iOS"
                : ua.contains("windows") ? "Windows"
                : ua.contains("mac os") ? "macOS"
                : ua.contains("cros") ? "ChromeOS"
                : ua.contains("linux") ? "Linux"
                : "sistema desconhecido";
        return browser + " no " + system;
    }

    static String approximate(@Nullable String address) {
        if (address == null || address.isBlank()) {
            return "desconhecido";
        }
        if (address.contains(":")) {
            String[] groups = address.split(":");
            return groups.length >= 3 ? groups[0] + ":" + groups[1] + ":" + groups[2] + "::/48" : address;
        }
        int lastDot = address.lastIndexOf('.');
        return lastDot > 0 ? address.substring(0, lastDot) + ".0/24" : address;
    }
}
