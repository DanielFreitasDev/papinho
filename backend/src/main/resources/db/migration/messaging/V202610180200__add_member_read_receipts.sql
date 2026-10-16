-- PV-06: confirmação de leitura sobreposta por conversa. Nulo segue o padrão: global na individual, ligada no grupo.
ALTER TABLE conversation_members ADD COLUMN read_receipts BOOLEAN;
