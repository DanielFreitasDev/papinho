package io.github.labdev2025cmd.papinho.identity;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import io.github.labdev2025cmd.papinho.shared.qr.QrCodes;
import io.github.labdev2025cmd.papinho.shared.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Botão "Convidar amigos" (ID-07): cria convites com link e QR Code, lista e revoga. */
@RestController
@RequestMapping("/api/v1/invites")
class InviteController {

    private final InviteService invites;

    InviteController(InviteService invites) {
        this.invites = invites;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CreatedInviteResponse create(@Valid @RequestBody CreateInviteRequest body) {
        InviteService.CreatedInvite created = invites.create(CurrentUser.id(), Duration.ofHours(body.validHours()),
                body.maxUses());
        String url = ServletUriComponentsBuilder.fromCurrentContextPath().path("/register")
                .queryParam("invite", created.token()).build().toUriString();
        return new CreatedInviteResponse(created.invite(), url, QrCodes.svg(url, "QR Code do convite"));
    }

    @GetMapping
    List<InviteService.InviteView> list() {
        return invites.list(CurrentUser.id());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@PathVariable UUID id) {
        invites.revoke(CurrentUser.id(), id);
    }

    /** Público: a tela de cadastro confere o convite antes de pedir os dados. */
    @GetMapping("/status")
    InviteStatusResponse status(@RequestParam @NotBlank @Size(max = 128) String token, HttpServletRequest request) {
        return new InviteStatusResponse(invites.isUsable(token, request.getRemoteAddr()));
    }

    /** @param validHours de 1 hora a 30 dias; @param maxUses de 1 a 100 */
    record CreateInviteRequest(@Min(1) @Max(720) int validHours, @Min(1) @Max(100) int maxUses) {
    }

    record CreatedInviteResponse(InviteService.InviteView invite, String url, String qrSvg) {
    }

    record InviteStatusResponse(boolean usable) {
    }
}
