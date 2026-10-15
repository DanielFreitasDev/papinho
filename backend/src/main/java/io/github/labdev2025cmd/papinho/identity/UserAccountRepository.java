package io.github.labdev2025cmd.papinho.identity;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByUsername(String username);

    boolean existsByUsername(String username);

    @Query("select u from UserAccount u where (:query = '' or u.username like concat('%', :query, '%')) order by u.username")
    Page<UserAccount> search(String query, Pageable pageable);
}
