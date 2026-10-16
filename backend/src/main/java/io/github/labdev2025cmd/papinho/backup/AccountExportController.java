package io.github.labdev2025cmd.papinho.backup;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import io.github.labdev2025cmd.papinho.shared.security.CurrentUser;

/** Exportar os dados da própria conta (BK-02). */
@RestController
class AccountExportController {

    private final AccountExport export;

    AccountExportController(AccountExport export) {
        this.export = export;
    }

    /** ZIP com {@code dados.json} e, com {@code files=true}, os arquivos enviados; até 10 por hora. */
    @GetMapping("/api/v1/account/export")
    ResponseEntity<StreamingResponseBody> export(@RequestParam(defaultValue = "false") boolean files) {
        UUID me = CurrentUser.id();
        export.checkLimit(me);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("papinho-meus-dados.zip", StandardCharsets.UTF_8).build().toString())
                .body(output -> export.write(me, files, output));
    }
}
