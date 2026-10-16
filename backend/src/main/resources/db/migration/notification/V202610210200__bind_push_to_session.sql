-- A assinatura de push vale enquanto existir a sessão que a criou: revogar a sessão, trocar a senha, desativar a
-- conta ou a sessão expirar cortam o push daquele navegador. A chave fica num atributo da sessão (sobrevive à troca
-- de id no login); as assinaturas antigas, sem chave, caem no próximo envio e voltam quando o app abrir de novo.
ALTER TABLE push_subscriptions ADD COLUMN session_key VARCHAR(36);
