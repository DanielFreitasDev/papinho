-- Código TOTP de uso único (RFC 6238, 5.2): guarda o passo do último código aceito no login; o mesmo código (ou um
-- anterior) não vale de novo, mesmo dentro da janela de ±30 s.
ALTER TABLE user_accounts ADD COLUMN totp_last_step BIGINT;
