package io.github.labdev2025cmd.papinho.presence;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.labdev2025cmd.papinho.shared.security.CurrentUser;

/** Online e visto por último de alguém (RT-03). */
@RestController
@RequestMapping("/api/v1/presence")
class PresenceController {

    private final PresenceService presence;

    PresenceController(PresenceService presence) {
        this.presence = presence;
    }

    @GetMapping("/{userId}")
    PresenceService.PresenceView get(@PathVariable UUID userId) {
        return presence.view(CurrentUser.id(), userId);
    }
}
