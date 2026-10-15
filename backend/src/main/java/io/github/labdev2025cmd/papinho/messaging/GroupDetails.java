package io.github.labdev2025cmd.papinho.messaging;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Dados e permissões de um grupo (GR-01) e o convite por link (GR-02). */
@Entity
@Table(name = "group_details")
class GroupDetails {

    static final int MAX_NAME = 100;
    static final int MAX_DESCRIPTION = 2048;

    @Id
    private UUID conversationId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    private @Nullable UUID photoMediaId;

    @Column(nullable = false)
    private boolean onlyAdminsSend;

    @Column(nullable = false)
    private boolean onlyAdminsEditInfo;

    @Column(nullable = false)
    private boolean onlyAdminsAddMembers;

    @Column(nullable = false)
    private boolean joinApproval;

    private @Nullable String inviteCode;

    @Column(nullable = false)
    private Instant updatedAt;

    protected GroupDetails() {
        this.conversationId = new UUID(0, 0);
        this.name = "";
        this.description = "";
        this.updatedAt = Instant.EPOCH;
    }

    GroupDetails(UUID conversationId, String name, String description, Instant now) {
        this.conversationId = conversationId;
        this.name = name;
        this.description = description;
        this.updatedAt = now;
    }

    UUID conversationId() {
        return conversationId;
    }

    String name() {
        return name;
    }

    String description() {
        return description;
    }

    @Nullable UUID photoMediaId() {
        return photoMediaId;
    }

    boolean onlyAdminsSend() {
        return onlyAdminsSend;
    }

    boolean onlyAdminsEditInfo() {
        return onlyAdminsEditInfo;
    }

    boolean onlyAdminsAddMembers() {
        return onlyAdminsAddMembers;
    }

    boolean joinApproval() {
        return joinApproval;
    }

    @Nullable String inviteCode() {
        return inviteCode;
    }

    Instant updatedAt() {
        return updatedAt;
    }

    void rename(String name, Instant now) {
        this.name = name;
        this.updatedAt = now;
    }

    void describe(String description, Instant now) {
        this.description = description;
        this.updatedAt = now;
    }

    void changePhoto(@Nullable UUID photoMediaId, Instant now) {
        this.photoMediaId = photoMediaId;
        this.updatedAt = now;
    }

    void changeSettings(boolean onlyAdminsSend, boolean onlyAdminsEditInfo, boolean onlyAdminsAddMembers,
            boolean joinApproval, Instant now) {
        this.onlyAdminsSend = onlyAdminsSend;
        this.onlyAdminsEditInfo = onlyAdminsEditInfo;
        this.onlyAdminsAddMembers = onlyAdminsAddMembers;
        this.joinApproval = joinApproval;
        this.updatedAt = now;
    }

    void changeInviteCode(@Nullable String inviteCode, Instant now) {
        this.inviteCode = inviteCode;
        this.updatedAt = now;
    }
}
