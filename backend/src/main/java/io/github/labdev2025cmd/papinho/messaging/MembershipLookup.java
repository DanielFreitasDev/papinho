package io.github.labdev2025cmd.papinho.messaging;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.labdev2025cmd.papinho.privacy.ConversationMembership;

/** Participação nas conversas para o módulo de privacidade (conversas trancadas, PV-03). */
@Component
class MembershipLookup implements ConversationMembership {

    private final ConversationMemberRepository members;

    MembershipLookup(ConversationMemberRepository members) {
        this.members = members;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isMember(UUID userId, UUID conversationId) {
        return members.findById(new ConversationMember.Key(conversationId, userId))
                .filter(member -> member.state() == MemberState.ACTIVE || member.state() == MemberState.REQUEST)
                .isPresent();
    }
}
