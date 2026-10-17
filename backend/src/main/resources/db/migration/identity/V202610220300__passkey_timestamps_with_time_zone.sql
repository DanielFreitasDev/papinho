-- §0: as datas das passkeys (tabela do Spring Security) eram TIMESTAMP sem fuso e dependiam do fuso da JVM. Os
-- valores gravados até aqui são lidos como UTC, o fuso do contêiner e do CI.
ALTER TABLE user_credentials
    ALTER COLUMN created TYPE TIMESTAMP WITH TIME ZONE USING created AT TIME ZONE 'UTC',
    ALTER COLUMN last_used TYPE TIMESTAMP WITH TIME ZONE USING last_used AT TIME ZONE 'UTC';
