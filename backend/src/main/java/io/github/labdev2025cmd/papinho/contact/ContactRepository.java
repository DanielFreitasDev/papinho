package io.github.labdev2025cmd.papinho.contact;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ContactRepository extends JpaRepository<Contact, Contact.Key> {

    @Query("select c from Contact c where c.key.ownerId = :ownerId order by c.createdAt")
    List<Contact> findByOwner(UUID ownerId);
}
