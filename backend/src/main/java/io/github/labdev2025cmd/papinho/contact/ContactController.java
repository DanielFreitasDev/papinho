package io.github.labdev2025cmd.papinho.contact;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import io.github.labdev2025cmd.papinho.identity.UserDirectory;
import io.github.labdev2025cmd.papinho.shared.qr.QrCodes;
import io.github.labdev2025cmd.papinho.shared.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Contatos, busca de pessoas, cartão por {@code @usuario}, link/QR próprio e bloqueios (CT-01, CT-03). */
@RestController
@RequestMapping("/api/v1")
class ContactController {

    private final ContactService contacts;
    private final UserDirectory users;

    ContactController(ContactService contacts, UserDirectory users) {
        this.contacts = contacts;
        this.users = users;
    }

    @GetMapping("/contacts")
    List<ContactService.ContactView> list() {
        return contacts.list(CurrentUser.id());
    }

    @PostMapping("/contacts")
    @ResponseStatus(HttpStatus.CREATED)
    ContactService.ContactView add(@Valid @RequestBody AddContactRequest body) {
        return contacts.add(CurrentUser.id(), body.username(), body.nickname());
    }

    @PatchMapping("/contacts/{userId}")
    ContactService.ContactView rename(@PathVariable UUID userId, @Valid @RequestBody RenameContactRequest body) {
        return contacts.rename(CurrentUser.id(), userId, body.nickname());
    }

    @DeleteMapping("/contacts/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable UUID userId) {
        contacts.remove(CurrentUser.id(), userId);
    }

    /**
     * Sugestões ao adicionar contato: até 20 pessoas da instância pelo nome ou pelo começo do {@code @usuario}
     * (aceita {@code @}), no mesmo formato do cartão. Menos de 2 caracteres devolve a lista vazia.
     */
    @GetMapping("/people")
    List<ContactService.PersonView> search(@RequestParam(defaultValue = "") String q) {
        return contacts.search(CurrentUser.id(), q);
    }

    /** Cartão de alguém pelo {@code @usuario} (aceita {@code @} e maiúsculas). */
    @GetMapping("/people/{username}")
    ContactService.PersonView lookup(@PathVariable String username) {
        return contacts.lookup(CurrentUser.id(), username);
    }

    /** Link e QR Code do próprio usuário para ser adicionado por outros. */
    @GetMapping("/contacts/my-link")
    MyLinkResponse myLink() {
        String username = users.findById(CurrentUser.id()).orElseThrow().username();
        String url = ServletUriComponentsBuilder.fromCurrentContextPath().path("/add/{username}")
                .buildAndExpand(username).toUriString();
        return new MyLinkResponse(url, QrCodes.svg(url, "QR Code para me adicionar"));
    }

    @GetMapping("/blocks")
    List<ContactService.BlockedView> blocked() {
        return contacts.blocked(CurrentUser.id());
    }

    @PutMapping("/blocks/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void block(@PathVariable UUID userId) {
        contacts.block(CurrentUser.id(), userId);
    }

    @DeleteMapping("/blocks/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unblock(@PathVariable UUID userId) {
        contacts.unblock(CurrentUser.id(), userId);
    }

    record AddContactRequest(@NotBlank @Size(max = 64) String username, @Nullable @Size(max = 64) String nickname) {
    }

    record RenameContactRequest(@Nullable @Size(max = 64) String nickname) {
    }

    record MyLinkResponse(String url, String qrSvg) {
    }
}
