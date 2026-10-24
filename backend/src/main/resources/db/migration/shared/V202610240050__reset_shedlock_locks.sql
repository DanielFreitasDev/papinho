-- O ShedLock com a hora do banco grava timezone('utc', CURRENT_TIMESTAMP), um horário sem fuso, que a coluna com
-- fuso convertia pelo fuso da sessão (o da JVM, que o driver JDBC manda ao conectar). Num servidor fora de UTC as
-- travas ficavam horas adiantadas ou atrasadas. A sessão agora é sempre UTC (connection-init-sql no
-- application.yaml); as travas gravadas antes podem estar deslocadas e saem: o próximo job recria a sua.
DELETE FROM shedlock;
