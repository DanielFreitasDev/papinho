package io.github.labdev2025cmd.papinho.messaging;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import io.github.labdev2025cmd.papinho.shared.qr.QrCodes;
import io.github.labdev2025cmd.papinho.shared.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Grupos (GR-01) e convites de grupo por link e QR Code (GR-02). */
@RestController
@RequestMapping("/api/v1/groups")
class GroupController {

    private final GroupService groups;

    GroupController(GroupService groups) {
        this.groups = groups;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ConversationService.ConversationView create(@Valid @RequestBody CreateGroupRequest body) {
        return groups.create(CurrentUser.id(), body.name(), body.description(),
                body.memberIds() == null ? List.of() : body.memberIds());
    }

    @PatchMapping("/{groupId}")
    ConversationService.ConversationView update(@PathVariable UUID groupId,
            @Valid @RequestBody UpdateGroupRequest body) {
        return groups.updateInfo(CurrentUser.id(), groupId, body.name(), body.description());
    }

    /** Foto do grupo: id de uma imagem enviada por tus, ou nulo para remover. */
    @PutMapping("/{groupId}/photo")
    ConversationService.ConversationView photo(@PathVariable UUID groupId, @RequestBody PhotoRequest body) {
        return groups.changePhoto(CurrentUser.id(), groupId, body.mediaId());
    }

    @PutMapping("/{groupId}/settings")
    ConversationService.ConversationView settings(@PathVariable UUID groupId,
            @Valid @RequestBody SettingsRequest body) {
        return groups.changeSettings(CurrentUser.id(), groupId, body.onlyAdminsSend(), body.onlyAdminsEditInfo(),
                body.onlyAdminsAddMembers(), body.joinApproval());
    }

    @GetMapping("/{groupId}/members")
    List<GroupService.MemberView> members(@PathVariable UUID groupId) {
        return groups.members(CurrentUser.id(), groupId);
    }

    @PostMapping("/{groupId}/members")
    List<GroupService.MemberView> add(@PathVariable UUID groupId, @Valid @RequestBody AddMembersRequest body) {
        return groups.addMembers(CurrentUser.id(), groupId, body.userIds());
    }

    @DeleteMapping("/{groupId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable UUID groupId, @PathVariable UUID userId) {
        groups.removeMember(CurrentUser.id(), groupId, userId);
    }

    @PutMapping("/{groupId}/admins/{userId}")
    List<GroupService.MemberView> promote(@PathVariable UUID groupId, @PathVariable UUID userId) {
        return groups.setAdmin(CurrentUser.id(), groupId, userId, true);
    }

    @DeleteMapping("/{groupId}/admins/{userId}")
    List<GroupService.MemberView> demote(@PathVariable UUID groupId, @PathVariable UUID userId) {
        return groups.setAdmin(CurrentUser.id(), groupId, userId, false);
    }

    /** Sai do grupo sem aviso na conversa. */
    @PostMapping("/{groupId}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void leave(@PathVariable UUID groupId) {
        groups.leave(CurrentUser.id(), groupId);
    }

    /** Link de convite e QR Code (criado na primeira vez); só admins. */
    @GetMapping("/{groupId}/invite")
    InviteResponse invite(@PathVariable UUID groupId) {
        return inviteResponse(groups.inviteCode(CurrentUser.id(), groupId));
    }

    @PostMapping("/{groupId}/invite/reset")
    InviteResponse resetInvite(@PathVariable UUID groupId) {
        return inviteResponse(groups.resetInvite(CurrentUser.id(), groupId));
    }

    @DeleteMapping("/{groupId}/invite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeInvite(@PathVariable UUID groupId) {
        groups.revokeInvite(CurrentUser.id(), groupId);
    }

    @GetMapping("/{groupId}/requests")
    List<GroupService.MemberView> requests(@PathVariable UUID groupId) {
        return groups.joinRequests(CurrentUser.id(), groupId);
    }

    @PostMapping("/{groupId}/requests/{userId}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void approve(@PathVariable UUID groupId, @PathVariable UUID userId) {
        groups.decideJoin(CurrentUser.id(), groupId, userId, true);
    }

    @PostMapping("/{groupId}/requests/{userId}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void decline(@PathVariable UUID groupId, @PathVariable UUID userId) {
        groups.decideJoin(CurrentUser.id(), groupId, userId, false);
    }

    @GetMapping("/invites/{code}")
    GroupService.InvitePreview preview(@PathVariable @Pattern(regexp = CODE) String code) {
        return groups.preview(CurrentUser.id(), code);
    }

    @PostMapping("/invites/{code}/join")
    GroupService.JoinResult join(@PathVariable @Pattern(regexp = CODE) String code) {
        return groups.join(CurrentUser.id(), code);
    }

    private static final String CODE = "[A-Za-z0-9_-]{8,32}";

    private static InviteResponse inviteResponse(String code) {
        String url = ServletUriComponentsBuilder.fromCurrentContextPath().path("/g/{code}").buildAndExpand(code)
                .toUriString();
        return new InviteResponse(code, url, QrCodes.svg(url, "QR Code do convite do grupo"));
    }

    record CreateGroupRequest(@NotBlank @Size(max = GroupDetails.MAX_NAME) String name,
            @Nullable @Size(max = GroupDetails.MAX_DESCRIPTION) String description,
            @Nullable @Size(max = GroupService.MAX_MEMBERS) List<@NotNull UUID> memberIds) {
    }

    record UpdateGroupRequest(@Nullable @Size(max = GroupDetails.MAX_NAME) String name,
            @Nullable @Size(max = GroupDetails.MAX_DESCRIPTION) String description) {
    }

    record PhotoRequest(@Nullable UUID mediaId) {
    }

    record SettingsRequest(boolean onlyAdminsSend, boolean onlyAdminsEditInfo, boolean onlyAdminsAddMembers,
            boolean joinApproval) {
    }

    record AddMembersRequest(@NotEmpty @Size(max = 256) List<@NotNull UUID> userIds) {
    }

    record InviteResponse(String code, String url, String qrSvg) {
    }
}
