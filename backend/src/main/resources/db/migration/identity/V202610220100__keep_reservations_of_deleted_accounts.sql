-- ID-03: o @usuario de uma conta excluída (o atual e os antigos ainda reservados) continua reservado, sem dono,
-- até o fim do prazo, para que ninguém assuma o nome que os contatos conheciam.
ALTER TABLE username_reservations ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE username_reservations DROP CONSTRAINT fk_username_reservations_user;
ALTER TABLE username_reservations
    ADD CONSTRAINT fk_username_reservations_user FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE SET NULL;
