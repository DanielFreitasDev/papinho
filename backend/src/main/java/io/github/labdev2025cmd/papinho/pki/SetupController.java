package io.github.labdev2025cmd.papinho.pki;

import java.nio.charset.StandardCharsets;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

/**
 * Página {@code /setup} e downloads da CA da LAN (PK-03, PK-04). Disponível também na porta HTTP, porque é o
 * único jeito de o aparelho obter a CA antes de confiar no HTTPS. Só existe com {@code app.pki.enabled=true}.
 */
@RestController
@RequestMapping("/setup")
@ConditionalOnBooleanProperty("app.pki.enabled")
class SetupController {

    private static final MediaType X509_CA = MediaType.parseMediaType("application/x-x509-ca-cert");
    private static final MediaType PEM = MediaType.parseMediaType("application/x-pem-file");
    private static final MediaType MOBILECONFIG = MediaType.parseMediaType("application/x-apple-aspen-config");
    private static final MediaType SHELL = MediaType.parseMediaType("text/x-shellscript;charset=UTF-8");
    private static final CacheControl NO_CACHE = CacheControl.noCache();

    private final SetupService setup;

    SetupController(SetupService setup) {
        this.setup = setup;
    }

    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> page() {
        SetupMaterial material = setup.material();
        String command = "curl -fsSL " + setup.setupUrl() + "/install-ca.sh | sh";
        String html = SetupService.template("pki/setup.html")
                .replace("{{FINGERPRINT}}", HtmlUtils.htmlEscape(material.sha256Fingerprint()))
                .replace("{{HOSTNAME}}", HtmlUtils.htmlEscape(material.hostname()))
                .replace("{{APP_URL}}", HtmlUtils.htmlEscape(setup.appUrl()))
                .replace("{{SETUP_URL}}", HtmlUtils.htmlEscape(setup.setupUrl()))
                .replace("{{SCRIPT_CMD}}", HtmlUtils.htmlEscape(command))
                .replace("{{QR_SVG}}", setup.qrCodeSvg());
        return ResponseEntity.ok().cacheControl(NO_CACHE).contentType(new MediaType(MediaType.TEXT_HTML,
                StandardCharsets.UTF_8)).body(html);
    }

    @GetMapping("/ca.crt")
    ResponseEntity<byte[]> caDer() {
        return download(setup.caDer(), X509_CA, "papinho-ca.crt");
    }

    @GetMapping("/ca.pem")
    ResponseEntity<byte[]> caPem() {
        return download(setup.caPem().getBytes(StandardCharsets.US_ASCII), PEM, "papinho-ca.pem");
    }

    @GetMapping("/papinho-ca.mobileconfig")
    ResponseEntity<byte[]> mobileConfig() {
        return download(setup.mobileConfig().getBytes(StandardCharsets.UTF_8), MOBILECONFIG,
                "papinho-ca.mobileconfig");
    }

    @GetMapping("/install-ca.sh")
    ResponseEntity<String> installScript() {
        return ResponseEntity.ok().cacheControl(NO_CACHE).contentType(SHELL).body(setup.installScript());
    }

    @GetMapping("/qr.svg")
    ResponseEntity<String> qrCode() {
        return ResponseEntity.ok().cacheControl(NO_CACHE).contentType(MediaType.parseMediaType("image/svg+xml"))
                .body(setup.qrCodeSvg());
    }

    @GetMapping("/setup.css")
    ResponseEntity<String> stylesheet() {
        return ResponseEntity.ok().cacheControl(NO_CACHE).contentType(MediaType.parseMediaType("text/css;charset=UTF-8"))
                .body(SetupService.template("pki/setup.css"));
    }

    @GetMapping("/setup.js")
    ResponseEntity<String> script() {
        return ResponseEntity.ok().cacheControl(NO_CACHE)
                .contentType(MediaType.parseMediaType("text/javascript;charset=UTF-8"))
                .body(SetupService.template("pki/setup.js"));
    }

    private static ResponseEntity<byte[]> download(byte[] body, MediaType type, String filename) {
        return ResponseEntity.ok()
                .cacheControl(NO_CACHE)
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(body);
    }
}
