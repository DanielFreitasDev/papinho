package io.github.labdev2025cmd.papinho.profile;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ProfileRepository extends JpaRepository<Profile, UUID> {

    List<Profile> findByUserIdIn(Collection<UUID> userIds);
}
