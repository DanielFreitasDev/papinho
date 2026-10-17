---
name: Detalhe do PostgreSQL nos logs e erros que nascem fora do Spring MVC
description: logServerErrorDetail=false; /error e a válvula do Tomcat precisam de Problem Details próprios
type: project
category: gotcha
date: 2026-10-17
---
- A violação de constraint loga `Detail: Key (username)=(...)` pelo `org.hibernate.orm.jdbc.error` e põe o mesmo
  texto na exceção. `spring.datasource.hikari.data-source-properties.logServerErrorDetail=false` faz o PgJDBC
  omitir o Detail (dados pessoais) em logs e exceções (achado A04).
- O que o `StrictHttpFirewall` recusa (`//`, `;jsessionid`, método estranho) vira `sendError(400)` e chega ao
  `/error`; o `TRACE` também. Um `ErrorController` próprio (`ProblemErrorController`) basta, sem
  `RequestRejectedHandler`: mas ele precisa pôr os cabeçalhos de segurança à mão (o filtro do Spring Security
  não rodou no pedido original).
- O `%00` no caminho é recusado pelo próprio Tomcat, antes da aplicação, com a página HTML do
  `ErrorReportValve`. Troque a classe da válvula no host: `factory.addContextCustomizers(context ->
  ((StandardHost) context.getParent()).setErrorReportValveClass(...))`.
- MockMvc não passa pelo Tomcat: prove esses casos com servidor real e socket cru (um cliente HTTP normaliza
  `//` e `..`).
- Um `@RestController` aninhado numa `@TestConfiguration` é achado pelo scan de componentes do classpath de
  teste; registrá-lo também por `@Bean` dá "Ambiguous mapping".

**Evidência:** `IdentityIT.shouldResolveConcurrentRegistrationsWithSameUsername` (o log mutado volta a ter o
`@usuario`), `RejectedRequestsIT` (6 pedidos), commits `ce93651` e `2a393db`.
