# Modern Java & Spring Boot Engineering Guide — Rules for AI Coding Agents

## Applicability and interoperability — revision 1.1 (2026-09-13)

This guide is independently usable. Companion guides are optional references: apply only the ones actually present and relevant to the task. A missing companion never requires installing the whole set, inventing its contents, or stopping work covered by this guide. Paths and project names in examples are templates, not facts about the current repository.

Honor the agent runtime's permissions and higher-priority instructions. Within project guidance, follow the explicit task, then applicable repository instructions and established conventions, then documentation for the installed version, then this guide's defaults. Existing insecure behavior is not a convention to preserve: fix it when in scope and report other findings. New-project defaults never authorize a dependency upgrade, migration, or restructuring of an existing project.

When guides are combined, topic ownership is: `COMMITS.md` for commit messages and Git actions; `CONTEXT.md` for `AGENTS.md`, `CLAUDE.md`, and project learning; `STRUCTURE.md` for file placement and build wiring; `JAVA.md` for backend implementation; `UI.md` for shadcn composition and APIs; `RESPONSIVENESS.md` for shared responsive/accessibility/PWA criteria. Product requirements decide whether PWA/offline is needed. Each guide retains the essentials needed when its companions are absent.

Mandatory rules apply within the requested scope. Validate changed behavior and concrete risks; use a full application audit only for a new application, a cross-cutting change, or an explicitly requested audit. Stop when the applicable acceptance criteria pass, or report a concrete environment blocker and the pending checks. Never claim an unexecuted check passed.

Deliver one report: outcome, relevant verification, and material limitations. Include a tree only for structural work and a suggested commit message when useful or requested; neither must be the last line. If the project learning workflow is active, put its short `LEARNING:` status last. Writing a suggested message never authorizes a commit or push.

---

**Version:** 1.1 — reviewed 2026-09-13
**New-project baseline:** Java 25 LTS · Spring Boot 4.1.x · the selected Boot BOM for Spring, Hibernate, Jackson, JUnit, Testcontainers and Lombok. Existing projects retain their installed compatible stack unless migration is requested.
**Language policy:** all identifiers in English; all comments and JavaDoc in Brazilian Portuguese (pt-BR).

> **How this file is organized.** Start with the applicability contract and Parts 0–2 when backend work needs these defaults; load Parts 3–15 by task. Keep only short project-specific pointers in `AGENTS.md` (or the agent's entry file). Do not import this entire reference into every session. Complete file templates in Part 15 form one example feature; earlier fragments illustrate individual techniques and are identified as such.

---

## Part 0 — How to use this document (CORE)

### 0.1 Precedence (highest wins)

1. An explicit instruction from the user in the current task.
2. Repository instructions (`AGENTS.md` or the agent's entry file) and conventions already present in the codebase being edited (naming, structure, libraries, formatting). Consistency with existing code beats this guide.
3. Documentation for the installed versions of the stack.
4. This guide.
5. General best practices.

If a rule in this guide conflicts with the task, state the exception in one sentence and apply as much of the guide as still makes sense. Never silently ignore a rule.

### 0.2 Agent working rules (non-negotiable)

- **Never invent APIs, dependencies, versions, properties or annotations.** If you are not certain a class, method, Maven coordinate or configuration key exists in the pinned versions (Part 1), say so and verify against the official documentation before using it. A compile error caused by a hallucinated import is the single most common and most expensive failure mode.
- **Respect the installed stack.** For a new Boot 4 project, use the Boot 4 APIs in Part 1. In an existing Boot 3 project, use the correct Boot 3 starters/packages until an explicitly scoped migration is authorized; this guide never authorizes that migration by itself.
- **Do not refactor what was not requested.** Touch only the files and methods the task requires. If you notice a problem elsewhere, mention it; do not fix it unasked.
- **Follow existing conventions before this guide** when editing existing code (see 0.1).
- **Deliver complete, compilable code**: package declaration, all imports, all referenced classes (or a clear note that they already exist), no `// ...` placeholders inside methods that matter, no pseudo-code.
- **State assumptions before code**, in at most 3–5 bullets, only when the request is ambiguous. Prefer asking one precise question over guessing when the ambiguity would change the architecture (e.g., "Is auth JWT or session-based?").
- **Prefer the simplest correct solution.** Priorities, in order: correctness → security → clarity → simplicity → testability → extensibility → performance. Do not add abstractions, patterns, or configuration "for the future" (YAGNI).
- **Security is not optional**: never log secrets or PII, never build SQL/JPQL by string concatenation with user input, never return entities from controllers, never disable CSRF/CORS/auth "temporarily", never commit credentials.
- **Tests are part of the deliverable** for any new service method, endpoint, repository query with custom logic, or bug fix (regression test). See Part 10 for the required test type per layer.
- **Migrations are part of the deliverable** for any schema change (Part 7.7). Never rely on `spring.jpa.hibernate.ddl-auto=update`.
- **Explain the "why", not the "what."** Comments document intent, trade-offs, invariants and non-obvious behavior. Do not narrate the code line by line.
- **When using a design pattern, say which one and why in one sentence** (in the JavaDoc or in the response). Use patterns only when they remove a real problem (Part 13).
- **Offer a suggested Conventional Commits message** when useful (Part 2.6); it is part of the single delivery report and does not authorize a commit or push.

### 0.3 Definition of Done (check before delivering)

- [ ] Compilation executed against the installed target stack with correct imports; if unavailable, explicitly report this gate as pending rather than passed.
- [ ] Identifiers in English; comments/JavaDoc in pt-BR; no accents in identifiers.
- [ ] DTOs are `record`s; no JPA entity crosses the controller boundary.
- [ ] Validation on request DTOs (`@Valid` + constraints); errors come back as RFC 9457 Problem Details.
- [ ] Transactions declared on the service layer with correct `readOnly` and boundaries.
- [ ] No N+1 introduced; queries reviewed for pagination + fetch-join pitfalls (Part 7.6).
- [ ] Flyway migration added for any schema change; `ddl-auto=validate`.
- [ ] Security: authorization checked (ownership/IDOR), no secrets in code/logs.
- [ ] Logs: SLF4J, correct level, structured context, no PII.
- [ ] Relevant tests written and named per Part 10; actual results recorded. Existing applicable architecture gates pass, or a concrete environment blocker is reported.
- [ ] No newly introduced deprecated API for the installed stack; an existing older stack was not upgraded as a side effect.
- [ ] Commit message in Conventional Commits format.

### 0.4 Response format for code tasks

1. (Optional) Assumptions — up to 5 bullets.
2. Code — one fenced block per file, each starting with a comment line `// path: src/main/java/...`.
3. Dependencies — only the starters/coordinates that must be added (Part 1.3).
4. Migration SQL — if the schema changed.
5. Tests.
6. Verification actually performed, pending environment checks, and an optional suggested commit message. If the project learning workflow is active, its `LEARNING:` status is the final line.

Keep prose short. Do not repeat the code in words.

---

## Part 1 — Target stack (CORE)

### 1.1 Version baseline and dependency ownership

| Component | Version | Notes |
|---|---|---|
| Java | **25 LTS** for these templates | Compile with `--release 25`. Java 21 projects need compatible alternatives to final Java 25 APIs, not a lower release flag on this source. Choose other releases only through the project support policy. |
| Spring Boot | **4.1.x** for new projects | Pin one released patch in the parent/BOM. Revision fixture: 4.1.1. Resolve the entire managed dependency set from that BOM rather than combining a manually selected Spring/Hibernate/Jackson matrix. |
| Spring Framework | Boot BOM managed | JSpecify null-safety, API versioning, `RestTestClient`, resilience (`@Retryable`, `@ConcurrencyLimit`), HTTP Service Clients, `JmsClient`, `BeanRegistrar`. |
| Spring Security | Boot BOM managed | Lambda DSL mandatory (`.and()` removed), `authorizeHttpRequests` only, `PathPatternRequestMatcher` only, `AuthorizationManager` only, `csrf.spa()`, passkeys/MFA factors. |
| Spring Data | Boot BOM managed | Derived queries rendered as JPQL (query-structure caching), `PredicateSpecification`/`UpdateSpecification`/`DeleteSpecification`, `Specification.unrestricted()`, AOT repositories, JSpecify. |
| Hibernate ORM | Boot BOM managed | JPA 3.2. `@UuidGenerator(style = VERSION_7)`, `@SoftDelete`, `hibernate-processor` (replaces `hibernate-jpamodelgen`). |
| Jackson | 3.x (`tools.jackson.*`) | Annotations stay in `com.fasterxml.jackson.annotation`. `JsonMapper` replaces `ObjectMapper` bean. Jackson 2 support is deprecated. |
| JUnit | Boot BOM managed | Java 17 baseline, single version for Platform/Jupiter/Vintage, JSpecify, `@ParameterizedClass`. JUnit 4 support in Spring is deprecated — never use it. |
| Mockito | Boot BOM managed | Use `MockitoExtension` for `@Mock`/`@Captor`. |
| AssertJ | Boot BOM managed | Default assertion library. |
| Testcontainers | Boot BOM managed | Artifacts renamed `testcontainers-<module>`; classes relocated to `org.testcontainers.<module>` (e.g., `org.testcontainers.postgresql.PostgreSQLContainer`). JUnit 4 removed. |
| Flyway | Boot BOM managed | Now requires `spring-boot-starter-flyway` (plus `flyway-database-postgresql` for PostgreSQL). |
| Lombok | Boot BOM managed | Use the BOM-supported release and annotation processor version; do not invent a newer minimum. |
| MapStruct | **1.6.3** stable reference | Optional, explicitly pinned outside the Boot BOM; use only for mappings that justify it. |
| springdoc-openapi | **3.1.1** reference for Boot 4 | Outside the Boot BOM; verify its compatibility matrix. A Boot 3 project uses the supported 2.x line. |
| ArchUnit | **1.5.0**, `archunit-junit6` for JUnit 6 | Not Boot-managed; pin explicitly. Keep `archunit-junit5` for a JUnit 5 project with a compatible version. |
| Spring Modulith | 2.x | Optional; Boot 4 baseline, Jackson 3, module-specific Flyway migrations. |
| Database | PostgreSQL 17/18 | PostgreSQL 18 has native `uuidv7()`. |
| Build | Maven 3.9.x with wrapper (`./mvnw`) or Gradle 9 (Kotlin DSL) | Boot 4 supports Gradle 8.14+ and 9. |
| GraalVM (optional) | 25+ | Only if native image is a stated requirement. |

### 1.2 Boot 4 migration reference — apply only to a Boot 4 project

| Older APIs in the migration surface | Boot 4 equivalents for a Boot 4 project |
|---|---|
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| `spring-boot-starter-aop` | `spring-boot-starter-aspectj` |
| `spring-boot-starter-oauth2-resource-server` | `spring-boot-starter-security-oauth2-resource-server` |
| Flyway/Liquibase as bare dependency | `spring-boot-starter-flyway` / `spring-boot-starter-liquibase` |
| One `spring-boot-starter-test` for everything | Per-technology test starters: `spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`, `spring-boot-starter-security-test`, `spring-boot-starter-flyway-test`, `spring-boot-starter-validation-test`, `spring-boot-starter-restclient-test`, `spring-boot-starter-actuator-test`. Each brings `spring-boot-starter-test` transitively. |
| `@MockBean` / `@SpyBean` | `@MockitoBean` / `@MockitoSpyBean` (`org.springframework.test.context.bean.override.mockito`) — on test class fields or on the class for shared mocks; **not** in `@Configuration` classes. |
| `@SpringBootTest` auto-provides `MockMvc`/`TestRestTemplate` | Add `@AutoConfigureMockMvc` or `@AutoConfigureRestTestClient` explicitly. Prefer `RestTestClient` over `TestRestTemplate`. |
| `org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest` | `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` (same for `AutoConfigureMockMvc`) |
| `org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest` | `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest` (test annotations follow `org.springframework.boot.<tech>.test.autoconfigure`) |
| `org.springframework.boot.autoconfigure.domain.EntityScan` | `org.springframework.boot.persistence.autoconfigure.EntityScan` |
| `com.fasterxml.jackson.databind.ObjectMapper` bean | `tools.jackson.databind.json.JsonMapper` bean; customize with `JsonMapperBuilderCustomizer` |
| `@JsonComponent`, `@JsonMixin` | `@JacksonComponent`, `@JacksonMixin` |
| Jackson read/write property families | `spring.jackson.read.*` / `write.*` still configure stream features; `spring.jackson.json.read.*` / `json.write.*` configure JSON-specific features. Choose by the actual feature enum; do not rename every property mechanically. |
| Custom `HttpMessageConverters` bean | `ServerHttpMessageConvertersCustomizer` / `ClientHttpMessageConvertersCustomizer` or `WebMvcConfigurer#configureMessageConverters(HttpMessageConverters.ServerBuilder)` |
| `org.springframework.lang.Nullable` / `@NonNull` | `org.jspecify.annotations.Nullable` / `@NonNull` / `@NullMarked` |
| `RestTemplate` | `RestClient` (or HTTP Service interfaces with `@HttpExchange`). `RestTemplate` is deprecated in docs since 7.0 and `@Deprecated` in 7.1. |
| Spring Retry (`org.springframework.retry`) | `org.springframework.core.retry.RetryTemplate`, `@Retryable`, `@ConcurrencyLimit`, `@EnableResilientMethods` |
| `hibernate-jpamodelgen` | `hibernate-processor` |
| `javax.annotation.*`, `javax.inject.*` | `jakarta.annotation.*`, `jakarta.inject.*` (javax no longer supported) |
| Spring Security `.and()`, `authorizeRequests()`, `AntPathRequestMatcher`, `MvcRequestMatcher`, `WebSecurityConfigurerAdapter` | Lambda DSL, `authorizeHttpRequests()`, `PathPatternRequestMatcher` (default; pass absolute patterns), `SecurityFilterChain` beans |
| `spring.dao.exceptiontranslation.enabled` | `spring.persistence.exceptiontranslation.enabled` |
| `management.tracing.enabled` | `management.tracing.export.enabled` |
| Undertow | Removed (no Servlet 6.1 support). Tomcat 11 (default) or Jetty 12.1. |
| Testcontainers `org.testcontainers:postgresql`, `org.testcontainers.containers.PostgreSQLContainer` | `org.testcontainers:testcontainers-postgresql`, `org.testcontainers.postgresql.PostgreSQLContainer`; JUnit extension is `org.testcontainers:testcontainers-junit-jupiter` |

Other Boot 4 facts the agent should rely on: liveness/readiness health probes are **enabled by default**; JDK `HttpClient`-backed clients use virtual threads when `spring.threads.virtual.enabled=true`; `spring-boot-starter-opentelemetry` exports metrics + traces via OTLP; API versioning is auto-configured via `spring.mvc.apiversion.*`; DevTools live reload is off by default; `logging.console.enabled=false` disables console logging.

### 1.3 Dependency menu for a REST API (XML fragment)

Select only required capabilities. Boot manages versions except the explicit springdoc, ArchUnit and optional MapStruct versions below. This is a dependencies fragment, not a complete `pom.xml`; Part 15.8 describes the assembled validation fixture. A version pattern such as `4.1.x` in prose is never a Maven version literal.

```xml
<!-- Runtime -->
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-webmvc</artifactId></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-jackson</artifactId></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-flyway</artifactId></dependency>
<dependency><groupId>org.flywaydb</groupId><artifactId>flyway-database-postgresql</artifactId></dependency>
<dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><scope>runtime</scope></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security</artifactId></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security-oauth2-resource-server</artifactId></dependency> <!-- if JWT -->
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-opentelemetry</artifactId></dependency> <!-- metrics + traces via OTLP -->
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-aspectj</artifactId></dependency> <!-- only if @Observed/@Timed/@Retryable on beans are used -->
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-restclient</artifactId></dependency> <!-- only if calling other HTTP services -->
<dependency><groupId>org.springdoc</groupId><artifactId>springdoc-openapi-starter-webmvc-ui</artifactId><version>3.1.1</version></dependency>
<dependency><groupId>org.jspecify</groupId><artifactId>jspecify</artifactId></dependency> <!-- managed by Spring; explicit for clarity -->
<dependency><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId><scope>provided</scope></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-devtools</artifactId><scope>runtime</scope><optional>true</optional></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-docker-compose</artifactId><scope>runtime</scope><optional>true</optional></dependency>

<!-- Test -->
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-webmvc-test</artifactId><scope>test</scope></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa-test</artifactId><scope>test</scope></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security-test</artifactId><scope>test</scope></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-flyway-test</artifactId><scope>test</scope></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation-test</artifactId><scope>test</scope></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-restclient-test</artifactId><scope>test</scope></dependency> <!-- RestTestClient -->
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-testcontainers</artifactId><scope>test</scope></dependency>
<dependency><groupId>org.testcontainers</groupId><artifactId>testcontainers-postgresql</artifactId><scope>test</scope></dependency>
<dependency><groupId>org.testcontainers</groupId><artifactId>testcontainers-junit-jupiter</artifactId><scope>test</scope></dependency>
<dependency><groupId>com.tngtech.archunit</groupId><artifactId>archunit-junit6</artifactId><version>1.5.0</version><scope>test</scope></dependency>
<dependency><groupId>org.awaitility</groupId><artifactId>awaitility</artifactId><scope>test</scope></dependency>
```

When a task needs another technology, prefer a compatible Boot starter if one exists. Verify its actual artifact name; not every runtime starter has a matching `-test` starter. A directly declared dependency is appropriate when there is no suitable starter or the application needs only that library.

---

## Part 2 — Language, naming and documentation conventions (CORE)

### 2.1 Identifier language: English (no exceptions except domain terms)

- Classes, interfaces, records, enums, methods, variables, constants, packages, database tables and columns, JSON property names, configuration keys: **English**.
- **Exception — Brazilian regulatory/fiscal/legal domain terms with no faithful English equivalent stay in Portuguese, without accents**: `cpf`, `cnpj`, `cep`, `notaFiscal`, `nfe`, `boleto`, `pix`, `icms`, `iss`, `sped`, `cnae`, `comarca`, `fgts`, `inss`, `esocial`, `darf`. Never translate these (`TaxpayerRegistryNumber` for CPF breaks traceability with legislation and SEFAZ/Receita documentation). Combine naturally: `CpfValidator`, `NotaFiscalService`, `boletoDueDate`.
- **Never use accents or non-ASCII characters in identifiers**, even though the JLS allows them.
- Do not mix languages inside one identifier except for the domain-term exception above (`validateCpf` is correct; `validarCpf` is not).

### 2.2 Naming rules

| Element | Convention | Example |
|---|---|---|
| Package | lowercase, feature-based (Part 5.1) | `com.acme.billing.invoice` |
| Class / record / enum / interface | `PascalCase`, noun | `InvoiceService`, `CreateInvoiceRequest` |
| Method | `camelCase`, verb phrase | `issueInvoice`, `findByCustomerId` |
| Variable / parameter | `camelCase`, meaningful; no single letters except loop indices and lambdas with obvious scope | `dueDate`, `totalAmount` |
| Constant (`static final`) | `UPPER_SNAKE_CASE` | `MAX_ITEMS_PER_ORDER` |
| Boolean accessors | `isX`/`hasX`/`canX` | `isOverdue()` |
| Enum constants | `UPPER_SNAKE_CASE` | `OrderStatus.AWAITING_PAYMENT` |
| Generic type params | single uppercase letter or descriptive `PascalCase` | `T`, `ID`, `Request` |
| Test class | `<ClassUnderTest>Test` (unit/slice), `<Feature>IT` (integration) | `InvoiceServiceTest`, `InvoiceApiIT` |
| Test method | `should<Expectation>When<Condition>` or `<method>_<condition>_<expectation>`; `@DisplayName` in pt-BR | `shouldRejectWhenInvoiceAlreadyPaid` |
| DTO in | `<Action><Resource>Request` | `CreateInvoiceRequest`, `UpdateInvoiceRequest` |
| DTO out | `<Resource>Response`, `<Resource>SummaryResponse` | `InvoiceResponse` |
| Domain event | `<Resource><PastTenseVerb>Event` | `InvoiceIssuedEvent` |
| Exception | `<Problem>Exception` | `InvoiceNotFoundException` |
| Config properties | `<Feature>Properties` record | `PaymentGatewayProperties` |
| Mapper | `<Resource>Mapper` | `InvoiceMapper` |
| Spring bean suffixes | `Controller`, `Service`, `Repository`, `Config`, `Handler`, `Listener`, `Client`, `Scheduler` | |
| Database | `snake_case`, plural tables, singular columns, FK `<ref>_id`, index `ix_<table>_<cols>`, unique `uq_<table>_<cols>`, FK constraint `fk_<table>_<ref>` | `invoice_items`, `customer_id` |
| Flyway migration | `V<yyyyMMddHHmm>__<snake_description>.sql` | `V202609101430__create_invoices.sql` |

Avoid: abbreviations that are not universal (`cust`, `inv`), Hungarian notation, `Impl` suffix when there is a single implementation (do not create the interface in that case), `Util`/`Helper`/`Manager` grab-bag classes, `data`/`info`/`object` as name parts.

### 2.3 Comments and JavaDoc: Brazilian Portuguese

- **All comments and JavaDoc are written in pt-BR**, with normal accentuation (the source encoding is UTF-8; see 2.7).
- **JavaDoc is mandatory on**: public types, public and protected methods of services, domain classes, repositories with custom queries, configuration classes, and every public API of a library module. Not mandatory on trivial getters, record components that are self-explanatory, private methods, or test methods.
- JavaDoc content: what the type/method is for, preconditions, invariants, side effects, transactional behavior, thrown exceptions (`@throws`), and links (`{@link}`) to related types. Do not repeat the signature in words.
- **Inline comments explain the *why***: business rules, trade-offs, workarounds (with the issue link), non-obvious ordering constraints, performance decisions. Do not narrate the *what* — the code must be readable on its own.
- **New non-trivial methods get one short header comment block** describing the algorithm/flow in 2–5 lines when the flow is not obvious from the method body. Do not comment every line.
- Markdown JavaDoc (`///`, JEP 467, Java 23+) is allowed and preferred for new code when the project already uses it; otherwise keep classic `/** */`. Be consistent per project.
- Use `TODO(<name>): <text>` and `FIXME(<name>): <text>` sparingly, always with a ticket/issue reference.
- Never leave commented-out code. Delete it; Git has the history.

Example of the expected style:

```java
/**
 * Emite uma fatura para o pedido informado.
 *
 * <p>Regras: o pedido precisa estar em {@code PAID}; a numeração fiscal é sequencial por
 * empresa e obtida dentro da mesma transação para evitar lacunas. O evento
 * {@link InvoiceIssuedEvent} é publicado na transação; listeners AFTER_COMMIT aguardam o commit.</p>
 *
 * @param orderId identificador do pedido
 * @return a fatura emitida
 * @throws OrderNotFoundException se o pedido não existir
 * @throws InvalidOrderStateException se o pedido não estiver pago
 */
@Transactional
public InvoiceResponse issueInvoice(UUID orderId) {
    var order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));

    // A validação de estado fica no domínio (Order) para que a regra não vaze
    // para outros serviços que também precisem emitir faturas.
    order.ensureCanBeInvoiced();

    // Lock pessimista no contador fiscal: a numeração não pode ter buracos nem
    // duplicidade sob concorrência (exigência da SEFAZ).
    var sequence = fiscalSequenceRepository.lockForCompany(order.getCompanyId());
    var invoice = Invoice.issue(order, sequence.next());

    invoiceRepository.save(invoice);
    eventPublisher.publishEvent(new InvoiceIssuedEvent(invoice.getId()));
    return invoiceMapper.toResponse(invoice);
}
```

### 2.4 Log messages, error messages, user-facing text

- **Log messages: English**, consistent with framework logs. Use placeholders (`{}`), never string concatenation. Include identifiers as structured key-values (Part 9).
- **Validation and error messages shown to API consumers: externalized** in message bundles. Supply a default `messages.properties` (required for Boot auto-configuration), pt-BR fallback text and `messages_en.properties` for English requests. Configure the locale policy explicitly and disable system-locale fallback for deterministic behavior. Never hardcode user-facing error text in Java.
- **Problem Details `title`/`detail`**: resolved from message bundles by `MessageSource`, so they follow the request `Accept-Language`.
- **Exception messages inside the code (`new IllegalStateException("...")`)**: English, technical, meant for developers and logs.

### 2.5 Tests: naming and display

- Test method names in English (see 2.2); `@DisplayName` in pt-BR describing the business behavior.
- Structure tests as *Arrange / Act / Assert* (or *Given / When / Then*), separated by blank lines, optionally with `// Arrange` style comments in pt-BR (`// Preparação`, `// Ação`, `// Verificação`) when the test is long.

### 2.6 Commit messages: Conventional Commits

Format: `<type>(<scope>): <description in pt-BR, third-person present indicative, no trailing period>`; body (optional) in pt-BR explaining *why*; footer with `BREAKING CHANGE:` or issue refs.

The complete header targets 50 characters and must fit 72 unless the repository sets another limit. `COMMITS.md`, when present, owns this policy.

Types: `feat`, `fix`, `refactor`, `perf`, `test`, `docs`, `build`, `ci`, `chore`, `style`, `revert`. Scope = feature/module name.

```
feat(invoice): adiciona emissão de fatura com numeração fiscal sequencial

A numeração precisa ser contígua por empresa. Usamos lock pessimista no
contador para evitar lacunas sob concorrência.

Refs: #482
```

Suggest a message for the logical change when useful; put it in the single delivery report before any final learning status. Never mix unrelated changes in one commit, and never execute Git mutations merely because a message was suggested.

### 2.7 Encoding and formatting

- Source encoding **UTF-8** everywhere: `<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>`, `<project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>`, and in `maven-javadoc-plugin` set `<encoding>`, `<docencoding>` and `<charset>` to UTF-8 (accented pt-BR JavaDoc renders wrongly otherwise). JDK 18+ defaults `file.encoding` to UTF-8 (JEP 400), but the Javadoc plugin still needs explicit configuration.
- Formatting is enforced by tooling, not by hand: Spotless with `palantir-java-format` (or `google-java-format`; pick one per project and never argue about it in reviews). 4-space indentation, 120-column limit, one blank line between members, imports without wildcards (except static imports in tests), unused imports removed.
- File layout order: package → imports (static last) → JavaDoc → annotations → type. Inside a class: constants → fields → constructors → public methods → protected → private → nested types.
- Line endings LF. `.editorconfig` committed.

---

## Part 3 — Modern Java (21 → 25): what to use, when, and what to avoid

Target language level is 25. Use the features below by default; do not write Java 8/11-style code when a newer construct is clearer.

### 3.1 Records — the default for any immutable data carrier

Use `record` for: request/response DTOs, value objects (Money, Cpf, Email, DateRange), query projections, event payloads, configuration properties, command/query objects, composite map keys, return tuples from private methods.

Rules:
- For domain value objects, enforce invariants in the compact constructor. Inbound transport DTOs are different: let Bean Validation report invalid/null fields, rather than throwing an accidental constructor NPE before validation. Preserve invalid nulls until validation when making a defensive collection copy (Part 15.4); use `List<@NotNull @Valid Item>` when null elements are invalid.
- Make defensive copies of mutable components (`List.copyOf`, `Map.copyOf`) in the compact constructor; never expose arrays.
- Add static factory methods for alternative constructions (`Money.of(...)`, `DateRange.ofMonth(...)`).
- Add behavior methods when the concept has behavior (`money.plus(other)`, `range.contains(date)`). Records are not "structs".
- Prefer nested records for one-off shapes used by a single class (`private record ...`).
- Do not use Lombok `@Value`/`@Data` where a record fits (always, for immutable data).
- Bean Validation constraints go directly on record components (`record CreateOrderRequest(@NotNull UUID customerId, @NotEmpty List<@Valid ItemRequest> items)`).
- Jackson 3 deserializes records through the canonical constructor with no extra annotations. Use `@JsonProperty` only when the JSON name differs from the component name.

```java
/**
 * Valor monetário com moeda. Imutável; aritmética retorna novas instâncias.
 */
public record Money(BigDecimal amount, Currency currency) {

    // Invariantes ficam no construtor compacto: nenhuma instância inválida existe.
    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        // Normaliza a escala para a moeda para que equals/hashCode sejam confiáveis.
        amount = amount.setScale(currency.getDefaultFractionDigits(), RoundingMode.HALF_EVEN);
    }

    public static Money brl(String amount) {
        return new Money(new BigDecimal(amount), Currency.getInstance("BRL"));
    }

    public Money plus(Money other) {
        ensureSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    private void ensureSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currency mismatch: %s vs %s".formatted(currency, other.currency));
        }
    }
}
```

### 3.2 Sealed interfaces + pattern matching for `switch` — model closed hierarchies

Use `sealed` + records to model: domain states with data, command/result types, event hierarchies, payment methods, discriminated API payloads. Then `switch` with pattern matching and **no `default` branch**, so adding a new variant fails compilation everywhere it must be handled (exhaustiveness).

```java
/** Resultado de uma tentativa de pagamento. Selado: o compilador exige tratar todos os casos. */
public sealed interface PaymentResult permits PaymentResult.Approved, PaymentResult.Declined, PaymentResult.Pending {
    record Approved(String authorizationCode, Instant approvedAt) implements PaymentResult {}
    record Declined(String reasonCode, String message) implements PaymentResult {}
    record Pending(String trackingId) implements PaymentResult {}
}

// Uso: sem 'default' de propósito — um novo tipo de resultado quebra a compilação aqui.
String describe(PaymentResult result) {
    return switch (result) {
        case PaymentResult.Approved(var code, var at) -> "approved %s at %s".formatted(code, at);
        case PaymentResult.Declined(var reason, _)     -> "declined: " + reason;   // '_' = componente ignorado (Java 22+)
        case PaymentResult.Pending p                   -> "pending " + p.trackingId();
    };
}
```

Guards: `case Declined d when d.reasonCode().startsWith("FRAUD") -> ...`. Nested record patterns are allowed but keep depth ≤ 2 for readability.

Use `instanceof` pattern matching instead of cast-after-check: `if (obj instanceof Order order && order.isOpen())`.

Do not use sealed hierarchies for JPA entities (Hibernate proxies and inheritance mapping do not mix well with `sealed`).

### 3.3 `switch` expressions and enums

- Prefer `switch` **expressions** (`->`, returning a value) over statements; no fall-through, no `break`.
- Switching on an enum without `default` is exhaustive and is the preferred way to force handling of every constant.
- Enum with behavior: put the behavior in the enum (constant-specific bodies or a `switch` inside a method) instead of `switch`ing on the enum in ten places.
- Enums persisted with JPA use `@Enumerated(EnumType.STRING)` (Part 7.12); never rely on ordinal.

### 3.4 `var`

Use `var` for local variables when the type is obvious from the right-hand side (constructor call, factory, builder, cast) or when the type name is long and adds no information. Do not use `var` when the right side is a method call whose return type is not evident (`var result = service.process(x);` is bad; `var order = new Order(...)` is good). Never for fields, parameters, or return types (not allowed anyway). Lombok `val`/`var` are forbidden (Part 4).

### 3.5 Text blocks

Use `"""` for JPQL/SQL in `@Query`, JSON fixtures in tests, multi-line messages, templates. Keep the closing delimiter aligned with the content; use `\` line continuation to avoid trailing newlines when needed. Use `.formatted()` for interpolation, never `+` chains. String templates (JEP 430/459/465) were **withdrawn** in JDK 23 — do not use them.

### 3.6 Collections and streams

- Return **immutable** collections from public APIs: `List.of`, `List.copyOf`, `Set.copyOf`, `Map.copyOf`, `Collections.unmodifiableList`, `Stream.toList()`. Never return internal mutable lists from entities/aggregates; expose `Collections.unmodifiableList(items)` or a copy.
- `Stream.toList()` (Java 16+) instead of `collect(Collectors.toList())` when an unmodifiable list is acceptable (it always is for return values). Use `Collectors.toCollection(ArrayList::new)` when mutability is required.
- Prefer a `for` loop over a stream when: there are side effects, checked exceptions, early exit with complex state, or the stream needs more than ~4 operations to read clearly.
- Use `Collectors.groupingBy`, `partitioningBy`, `teeing` when they express intent better than manual maps.
- **Stream Gatherers** (`Stream.gather(...)`, JEP 485, Java 24+): use built-ins `Gatherers.windowFixed`, `windowSliding`, `fold`, `scan`, `mapConcurrent` for batching/windowing instead of index arithmetic. Write custom gatherers only for reusable intermediate operations.
- **Sequenced Collections** (Java 21): use `getFirst()`, `getLast()`, `reversed()`, `addFirst()` on `List`, `Deque`, `LinkedHashSet`, `LinkedHashMap` instead of `get(0)` / `get(size()-1)`.
- Prefer `EnumMap`/`EnumSet` for enum keys. Prefer `ArrayList` over `LinkedList` (practically always).
- Do not use `Optional` as a collection element, field, or parameter (see 3.7).
- Parallel streams: only for CPU-bound work on large in-memory data, never inside a request thread that holds a transaction/connection, never with I/O.

### 3.7 `Optional`

- Use `Optional` **only as a return type** for "may be absent" results (repository `findById`, lookups).
- Never as a field, constructor/method parameter, or collection element; never `Optional.get()` without a check — use `orElseThrow(() -> new NotFoundException(...))`, `map`, `flatMap`, `orElse`, `orElseGet`, `ifPresentOrElse`.
- Do not wrap collections in `Optional` — return an empty collection.
- `orElse(expensiveCall())` evaluates eagerly; use `orElseGet(() -> ...)`.

### 3.8 Null-safety with JSpecify (the Spring 7 standard)

- Add `org.jspecify:jspecify` (managed by Spring) and annotate every package with `@NullMarked` in `package-info.java`. From then on everything is non-null by default; mark the exceptions with `@Nullable`.
- Never use `org.springframework.lang.*`, `javax.annotation.*`, `jakarta.annotation.Nonnull`, or Lombok `@NonNull` for nullability *documentation*. Use JSpecify. (Lombok `@NonNull` generates a runtime check; if used, restrict it to constructor parameters and copy JSpecify annotations via `lombok.config` — Part 4.)
- Validate incoming nulls at boundaries with `Objects.requireNonNull(x, "x")` in constructors and public entry points; inside `@NullMarked` code, trust the types.
- Optional tooling: Error Prone + NullAway to enforce JSpecify at compile time. Recommended for new projects; add it in the build (Part 11) when the team agrees.
- Spring Data validates repository method nullability against `@NullMarked` (a `@Nullable`-less parameter receiving `null` throws early).

```java
// path: src/main/java/com/acme/billing/package-info.java
/** Módulo de faturamento. Tudo é não-nulo por padrão (JSpecify); exceções explícitas com @Nullable. */
@NullMarked
package com.acme.billing;

import org.jspecify.annotations.NullMarked;
```

### 3.9 Exceptions

- **Unchecked by default.** Checked exceptions only when the caller can realistically recover *and* the API is internal; never let checked exceptions leak from service interfaces. Wrap third-party checked exceptions in a domain/infrastructure unchecked exception, preserving the cause.
- Domain exceptions: a small **sealed** hierarchy rooted in an abstract `DomainException` (Part 15), each carrying an error code (enum) and message arguments, mapped to Problem Details in a single place (Part 6.6).
- Never swallow exceptions (`catch (Exception e) {}`), never `catch (Exception)` unless at a top-level boundary (scheduler, listener, controller advice), never log-and-rethrow (log once, at the boundary), never use exceptions for control flow.
- Exception messages: English, include identifiers, never include secrets or PII.
- `try-with-resources` for every `AutoCloseable`. Multi-catch (`catch (IOException | TimeoutException e)`) when handling is identical.
- Do not catch `NullPointerException`, `IllegalArgumentException` from your own code — fix the bug.
- Use `Exception#addSuppressed` semantics from try-with-resources; do not lose the original exception in `finally`.

### 3.10 Dates, time, money, identifiers

- **`java.time` only.** `Instant` for points in time (persisted as `timestamptz`), `LocalDate` for calendar dates, `OffsetDateTime` only at API boundaries where the offset is part of the contract, `ZonedDateTime` for scheduling in a business time zone (`America/Fortaleza`, `America/Sao_Paulo`), `Duration`/`Period` for spans. Never `java.util.Date`, `Calendar`, `SimpleDateFormat`, `java.sql.Timestamp` in application code.
- Inject a `java.time.Clock` bean and use `Instant.now(clock)` / `LocalDate.now(clock)` so time is testable (`Clock.fixed(...)` in tests). Never call `Instant.now()` directly in domain/service code.
- JSON: ISO-8601 strings (`2026-09-10T14:30:00Z`); never epoch numbers (Jackson 3 disables `WRITE_DATES_AS_TIMESTAMPS` by default — keep it that way).
- **Money**: `BigDecimal` with explicit scale and `RoundingMode` (`HALF_EVEN` for financial rounding unless the business says otherwise); or a `Money` record wrapping it. Never `double`/`float` for money. Database column `numeric(19,4)` (or the precision the business needs).
- **Identifiers**: `UUID` v7 for public entity IDs (time-ordered, index-friendly; Hibernate 7 `@UuidGenerator(style = VERSION_7)`, PostgreSQL 18 `uuidv7()`), or `Long` from a database sequence when IDs are internal. Never expose auto-increment IDs that reveal volume or enable enumeration. Wrap IDs in records (`record OrderId(UUID value)`) when the domain benefits from type safety; do not do it mechanically for every entity.
- **CPF/CNPJ**: validated string value objects, never numbers. CPF remains 11 digits. CNPJ has 14 characters: the first 12 accept uppercase ASCII letters A–Z and digits, and the last 2 are numeric check digits. Preserve existing numeric CNPJs. Remove only recognized formatting punctuation (`.`, `/`, `-`), normalize case with `Locale.ROOT`, then validate syntax and the Receita Federal check-digit algorithm; never strip all non-digits. Store an unformatted `varchar(14)`/`char(14)` string, preserve leading zeros, and format only for display. Alphabetic values use ASCII code minus 48 in the modulo-11 calculation. Verify frontend masks, API schemas, database constraints, integrations and the installed validator against both formats (e.g. `12.ABC.345/01DE-35`).

### 3.11 Concurrency (Java 21+)

- **Virtual threads are a new-project default for blocking web I/O**, enabled with `spring.threads.virtual.enabled=true` where supported. Verify the executor actually used by each server, scheduler, async method and messaging listener: this property does not replace every third-party/custom executor. Keep explicit concurrency and connection limits; use bounded parallelism for CPU-bound work.
- Never pool virtual threads. Create them per task (`Executors.newVirtualThreadPerTaskExecutor()`, `Thread.ofVirtual().start(...)`).
- Pinning: since JDK 24 (JEP 491) `synchronized` no longer pins the carrier; still prefer `ReentrantLock` for long-held locks and never block inside `synchronized` on JDK ≤ 23.
- **Connection pools are the real bottleneck** under virtual threads: size HikariCP (`spring.datasource.hikari.maximum-pool-size`) for the database, not for the number of threads; use timeouts (`connection-timeout`) so contention fails fast.
- **`ScopedValue`** (final in Java 25) is suitable for immutable, lexically scoped context in code you own. Bindings are thread-local; child-task inheritance is supported through `StructuredTaskScope`, whose API is still preview in Java 25. Arbitrary `Thread.startVirtualThread`, executors and `CompletableFuture` tasks do not inherit bindings automatically. Do not enable preview features just for context propagation. Keep framework-required `ThreadLocal` context (MDC, `SecurityContextHolder`) and use the framework's supported propagation mechanism for asynchronous work.
- **Structured Concurrency** (`StructuredTaskScope`, JEP 505) is still **preview** in 25. Do not use preview features in production code. For fan-out/fan-in use `CompletableFuture` with a virtual-thread executor or `Gatherers.mapConcurrent`.
- Always set timeouts on external calls (HTTP, DB, messaging). `CompletableFuture.orTimeout`/`completeOnTimeout`.
- Immutability first: share immutable data (records) across threads; for mutable shared state use `java.util.concurrent` (`ConcurrentHashMap`, `AtomicLong`, `LongAdder`) — never hand-rolled double-checked locking.
- `@Async` and `@Scheduled` require careful exception handling (exceptions vanish) — Part 12.

### 3.12 Generics

- No raw types, ever. Use bounded wildcards for API flexibility (PECS: `? extends` for producers, `? super` for consumers). Prefer generic methods over generic classes when the type parameter is only used in one method. Avoid deep generic signatures in public APIs; introduce a named type instead.

### 3.13 `equals`, `hashCode`, `toString`

- Records: generated, correct by construction (after normalization in the compact constructor).
- Value classes that cannot be records: implement all three consistently with `Objects.equals`/`Objects.hash`, or use Lombok `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` with explicit fields.
- JPA entities: **never** Lombok `@Data`/`@EqualsAndHashCode`; see Part 7.1 for the id-based implementation. `toString` on entities must not touch lazy associations.
- `toString` never includes secrets, tokens, passwords, full documents (CPF/CNPJ masked).

### 3.14 Other Java 22–25 features and how to treat them

| Feature | JEP | Status in 25 | Guidance |
|---|---|---|---|
| Unnamed variables and patterns `_` | 456 | Final (22) | Use for ignored lambda params, catch params, record pattern components. |
| Foreign Function & Memory API | 454 | Final (22) | Only for native interop; not in typical business apps. |
| Launch multi-file source programs | 458 | Final (22) | Scripts/tooling only. |
| Markdown documentation comments `///` | 467 | Final (23) | Allowed; be consistent per project. |
| Generational ZGC default (23); non-generational ZGC removed (24) | 474/490 | Final | Choose GC per workload (Part 11.6); G1 default is fine for most services. |
| Class-File API | 484 | Final (24) | Framework-level; Spring 7 uses it internally. |
| Stream Gatherers | 485 | Final (24) | See 3.6. |
| AOT class loading & linking / AOT cache | 483/514/515 | Final (24/25) | Use for startup time (Part 11.6). |
| Synchronize virtual threads without pinning | 491 | Final (24) | See 3.11. |
| Scoped Values | 506 | Final (25) | See 3.11. |
| Module Import Declarations `import module java.base;` | 511 | Final (25) | Allowed in scripts/tests; in production code prefer explicit imports (tooling, readability, and Spotless import ordering work better). |
| Compact Source Files & instance `main` | 512 | Final (25) | Scripts, demos, tooling only — never in a Spring application. |
| Flexible Constructor Bodies (statements before `super()`/`this()`) | 513 | Final (25) | Use to validate/normalize arguments before delegating; keeps constructors honest. |
| Key Derivation Function API | 510 | Final (25) | Only for custom crypto; prefer Spring Security's `PasswordEncoder`s. |
| Compact Object Headers | 519 | Final (25), opt-in `-XX:+UseCompactObjectHeaders` | Enable in containers to cut heap use (Part 11.6). |
| Structured Concurrency | 505 | Preview | Do not use in production. |
| Stable Values | 502 | Preview | Do not use in production. |
| Primitive types in patterns | 507 | Preview | Do not use in production. |
| String Templates | 430/459/465 | **Withdrawn** | Do not use. `String.formatted()` / `MessageFormat` / text blocks instead. |
| Security Manager | 486 | Permanently disabled (24) | Do not reference. |
| `sun.misc.Unsafe` memory access | 498 | Warnings (24), removal planned | Ensure libraries are updated; never use directly. |

### 3.15 Do not write (legacy or anti-idiomatic Java)

- Anonymous inner classes for functional interfaces (use lambdas/method references).
- `StringBuffer`, `Vector`, `Hashtable`, `Stack`, `Enumeration`, `Iterator` loops where for-each works.
- Manual `synchronized` collections (`Collections.synchronizedMap`) — use `java.util.concurrent`.
- `new Integer(1)`, boxing in hot loops, `==` on boxed types or strings.
- `finalize()`, `Object.clone()`, serialization via `Serializable` for domain objects (use JSON), `ObjectInputStream` on untrusted data (RCE risk).
- Reflection/`Class.forName` in business code; `System.exit`; `Thread.sleep` for synchronization; `Runtime.exec` with user input.
- Static mutable state, service locators, singletons implemented by hand (Spring manages scope).
- Deep inheritance hierarchies for code reuse; prefer composition and interfaces with default methods sparingly.
- `Object` as parameter/return type; `Map<String, Object>` as a domain model or API contract.
- Utility classes with hundreds of static methods; put behavior on the type that owns the data.

---

## Part 4 — Lombok policy

### 4.1 Status (September 2026)

Lombok is an optional project choice, with its version managed by the selected Boot BOM. These templates use a narrow subset for entities, constructor injection and loggers. Records cover immutable data carriers; avoid generated setters or equality that hide entity invariants. A repository using explicit constructors/getters is equally valid. Confirm Lombok's JDK support and use the same version on the annotation-processor path; a guessed future minimum is not a compatibility check.

### 4.2 Allowed / forbidden annotations

| Annotation | Verdict | Where / why |
|---|---|---|
| `@Slf4j` | **Allowed** (preferred if Lombok is present) | Any class needing a logger. Explicit alternative: `private static final Logger log = LoggerFactory.getLogger(Foo.class);`. Never `@Log4j2`/`@Log` — code against SLF4J. |
| `@RequiredArgsConstructor` | **Allowed** | Spring beans with `private final` dependencies → constructor injection without boilerplate. Add `@RequiredArgsConstructor(onConstructor_ = @Autowired)` only if there is more than one constructor (rare; avoid). |
| `@Getter` (class or field level) | **Allowed** | JPA entities and mutable configuration/holder classes. Not on records (redundant). |
| `@Setter` | **Allowed, field-level only, selectively** | Never on `id`, `version`, audit fields, or fields with invariants; prefer intention-revealing methods (`order.cancel(reason)`) over setters. Class-level `@Setter` on entities is forbidden. |
| `@NoArgsConstructor(access = AccessLevel.PROTECTED)` | **Allowed** | JPA entities (Hibernate needs a no-arg constructor; `protected` prevents misuse). |
| `@AllArgsConstructor` | **Discouraged** | Positional constructors with many parameters are error-prone; use static factories or builders. Allowed on small immutable non-record classes only. |
| `@Builder` | **Allowed with care** | Test data builders, complex request objects, and immutable classes that cannot be records. On JPA entities only together with a private/protected all-args constructor and **never as the only way to create the entity** (it bypasses invariants) — prefer a static factory method that enforces rules. `@Builder` works on records (`@Builder record X(...)`), but prefer plain constructors for records with ≤ 5 components. Use `@Builder.Default` for defaults; `@Singular` for collections. |
| `@With` | **Allowed** | Immutable classes/records where "copy with one change" is common. |
| `@ToString(onlyExplicitlyIncluded = true)` + `@ToString.Include` | **Allowed** | Entities (never include lazy associations or secrets). Records generate their own. |
| `@EqualsAndHashCode` | **Forbidden on JPA entities**; allowed on non-record value classes with `onlyExplicitlyIncluded = true` | Entities implement `equals/hashCode` by hand (Part 7.1). |
| `@Data` | **Forbidden** | Combines setters + `equals/hashCode` + `toString`: wrong for entities, unnecessary for DTOs (records). |
| `@Value` | **Forbidden** | Use `record`. |
| `@NonNull` (Lombok) | **Discouraged** | Generates a runtime `NullPointerException` check; acceptable on constructor parameters of non-Spring classes. For nullability *typing* use JSpecify (Part 3.8). |
| `@Jacksonized` | **Allowed only with `@Builder` on non-record classes** | Jackson 3 supported since Lombok 1.18.44 (configure the Jackson generation target in `lombok.config` per Lombok docs or you get a warning). Records need neither. |
| `@SneakyThrows` | **Test code only** | In production wrap checked exceptions explicitly. |
| `val`, `var` (Lombok) | **Forbidden** | Use Java `var`. |
| `@Cleanup` | **Forbidden** | Use try-with-resources. |
| `@Synchronized` | **Forbidden** | Use `ReentrantLock` / `java.util.concurrent`. |
| `@UtilityClass`, `@FieldDefaults`, `@Accessors(fluent/chain)`, `@ExtensionMethod`, `@Delegate`, `@Helper`, `@StandardException`, `@FieldNameConstants`, `@Tolerate`, `@SuperBuilder` (except for a deliberate class hierarchy of DTOs/builders) | **Forbidden** (experimental or obscures code) | Write plain Java. |

### 4.3 `lombok.config` (project root, committed)

```properties
# Interrompe a busca de lombok.config em diretórios acima do projeto.
config.stopBubbling = true
# Marca métodos gerados com @Generated: JaCoCo/SonarQube ignoram o código do Lombok na cobertura.
lombok.addLombokGeneratedAnnotation = true
# Nome padrão do campo de logger criado por @Slf4j.
lombok.log.fieldName = log
# Copia anotações de nulidade JSpecify para getters/construtores gerados.
lombok.copyableAnnotations += org.jspecify.annotations.Nullable
lombok.copyableAnnotations += org.jspecify.annotations.NonNull
# Bane o que a política do projeto proíbe (falha na compilação, não em code review).
lombok.data.flagUsage = error
lombok.value.flagUsage = error
lombok.val.flagUsage = error
lombok.var.flagUsage = error
lombok.cleanup.flagUsage = error
lombok.synchronized.flagUsage = error
lombok.fieldDefaults.flagUsage = error
lombok.experimental.flagUsage = error
lombok.sneakyThrows.flagUsage = warning
# Setters não retornam 'this' (evita estilo fluente que confunde com builders).
lombok.accessors.chain = false
lombok.toString.includeFieldNames = true
```

### 4.4 Build integration

- Maven: Lombok with `<scope>provided</scope>`; declare `annotationProcessorPaths` explicitly in `maven-compiler-plugin` in this order: `lombok`, `lombok-mapstruct-binding` (0.2.0, only if MapStruct is used), `mapstruct-processor`, `hibernate-processor` (if using JPA static metamodel), `spring-boot-configuration-processor`. Order matters: Lombok must run before MapStruct so generated getters are visible.
- Gradle: `compileOnly` + `annotationProcessor` for Lombok; same for tests (`testCompileOnly`, `testAnnotationProcessor`).
- IDE: enable annotation processing; do not commit IDE-specific Lombok settings.

### 4.5 Records vs Lombok decision table

| Need | Use |
|---|---|
| Immutable DTO / value object / event / projection | `record` |
| Immutable DTO with 8+ optional fields built incrementally | `record` + `@Builder` (Lombok) or a hand-written builder |
| JPA entity | class + `@Getter` + selective `@Setter` + `@NoArgsConstructor(PROTECTED)` + hand-written `equals/hashCode` |
| Spring bean | class + `@RequiredArgsConstructor` (or explicit constructor) |
| Mutable configuration holder | `@ConfigurationProperties` **record** (constructor binding) — no Lombok |
| Logger | `@Slf4j` |
| "Copy with change" | `record` + `@With` or explicit `withX` methods |

---

## Part 5 — Architecture

### 5.1 Package by feature (not by layer)

Organize the code by business capability. Each feature package owns its controller, service, domain, repository and DTOs, and exposes as little as possible (package-private by default). Package-by-layer (`controller/`, `service/`, `repository/` at the root) is **not** used: it hides coupling and makes every class public.

```
com.acme.billing
├── BillingApplication.java
├── shared/                      # código transversal REALMENTE compartilhado (pequeno!)
│   ├── config/                  # JacksonConfig, ClockConfig, OpenApiConfig, WebConfig
│   ├── error/                   # DomainException hierarchy, GlobalExceptionHandler, ErrorCode
│   ├── security/                # SecurityConfig, CurrentUser, JwtConverter
│   ├── persistence/             # AuditableEntity, JpaConfig, converters
│   └── web/                     # PageResponse, RequestIdFilter, ApiVersionConfig
├── customer/                    # feature (módulo lógico)
│   ├── Customer.java            # entidade/agregado (package-private ou public só se outra feature precisar)
│   ├── CustomerRepository.java  # package-private
│   ├── CustomerService.java     # public API da feature (ou uma interface CustomerApi)
│   ├── CustomerController.java  # package-private
│   ├── CustomerMapper.java
│   ├── dto/                     # CreateCustomerRequest, CustomerResponse ... (public: fazem parte do contrato)
│   └── event/                   # CustomerRegisteredEvent (public: consumido por outras features)
├── invoice/
│   ├── Invoice.java
│   ├── InvoiceItem.java
│   ├── InvoiceStatus.java
│   ├── InvoiceRepository.java
│   ├── InvoiceService.java
│   ├── InvoiceController.java
│   ├── InvoiceSpecifications.java
│   ├── dto/
│   ├── event/
│   └── internal/                # sub-pacote para detalhes (ex.: gerador de PDF, cliente SEFAZ)
└── payment/
    └── ...
```

Rules:
- A feature talks to another feature only through its public service/API type, its DTOs and its events — never through another feature's repository or entity.
- `shared/` must stay small; if it grows into a "common" dumping ground, split it into named modules (`shared/money`, `shared/audit`).
- For larger systems use **Spring Modulith** (`@ApplicationModule`, `ApplicationModules.of(App.class).verify()` in a test, event publication registry, module-specific Flyway migrations). It formalizes exactly this structure.
- One feature ≈ one aggregate root (or a small cluster). If a feature needs 20 classes at the top level, add sub-packages by concept (`invoice/pdf`, `invoice/fiscal`), not by layer.

### 5.2 Layers inside a feature and what each may do

| Layer | Responsibility | Must not |
|---|---|---|
| **Controller** (`@RestController`) | HTTP concerns only: map request → DTO, call one service method, map result → status/DTO, headers, content negotiation. Validation trigger (`@Valid`). | Contain business rules, call repositories, build queries, catch exceptions for mapping (that is the advice's job), know about entities. |
| **Application service** (`@Service`) | Use-case orchestration: load aggregates, call domain behavior, coordinate repositories, transactions, publish events, map to response DTOs (or delegate to a mapper). One public method per use case. | Contain HTTP types (`ResponseEntity`, `HttpStatus`, `HttpServletRequest`), implement business invariants that belong to the domain object, be a pass-through with no logic (then call the repository from a query service and keep it thin). |
| **Domain** (entities, value objects, domain services, events) | Business rules, invariants, state transitions. Plain Java + JPA annotations. | Depend on Spring web, on repositories (except through a domain service interface), on DTOs. |
| **Repository** (Spring Data interface) | Persistence and queries. Return entities, projections, `Page`/`Slice`/`Window`. | Contain business logic; be called from controllers; expose `List<Entity>` for unbounded queries. |
| **Infrastructure adapters** (`internal/`, clients, gateways) | Talk to external systems (HTTP clients, messaging, file storage, SEFAZ, e-mail). Implement a domain-side interface (port). | Leak external DTOs into the domain. |

Read/query use cases may bypass the domain (CQRS-lite): a `XxxQueryService` reads with projections directly into response records. Commands go through the aggregate.

### 5.3 Dependency rules — enforced with ArchUnit, not with code review

Every project has an `ArchitectureTest` (Part 15) asserting at minimum:
- Controllers depend only on services and DTOs; never on repositories or entities.
- Services do not depend on `org.springframework.web..`.
- Domain packages do not depend on `..controller..`, `..dto..`, `org.springframework.web..`, `jakarta.servlet..`.
- No cycles between feature packages (`slices().matching("com.acme.billing.(*)..").should().beFreeOfCycles()`).
- Repositories are interfaces in the feature package and are package-private or only accessed by their own feature's services.
- Naming: `@RestController` classes end with `Controller`, `@Service` with `Service`, repositories with `Repository`.
- No field injection (`@Autowired` on fields is forbidden).
- No `java.util.Date`/`Calendar` usage.

### 5.4 Dependency injection rules

- **Constructor injection only.** `private final` fields; one constructor (Lombok `@RequiredArgsConstructor` or explicit). No `@Autowired` on fields or setters. `@Autowired` on the single constructor is unnecessary.
- No circular dependencies. If Spring reports one, the design is wrong (extract the shared part or use events); never "fix" with `@Lazy` or `spring.main.allow-circular-references`.
- Interfaces only when there are (or will realistically be) multiple implementations, or when the interface is a port to infrastructure that tests replace. A single `FooService` class is fine — no `FooServiceImpl`.
- `@Configuration(proxyBeanMethods = false)` for configuration classes that do not call `@Bean` methods from each other (the common case) — cheaper startup, native-friendly.
- Prefer `@Bean` methods for third-party types and `@Component` scanning for your own classes. Never `new` a Spring-managed collaborator inside another bean.
- Use `@ConditionalOnProperty`/profiles for optional infrastructure, not `if (env.equals("prod"))`.
- Use `ObjectProvider<T>` for optional dependencies; never `@Autowired(required = false)` on fields.
- Bean scope is singleton; beans must be stateless or thread-safe.
- Prefer `ApplicationEventPublisher` + `@TransactionalEventListener` for decoupling features over direct calls when the callee is not needed for the caller's outcome.

### 5.5 Configuration properties

- All external configuration is bound to **`@ConfigurationProperties` records** with constructor binding, validated with `@Validated` + Bean Validation, and registered with `@ConfigurationPropertiesScan` on the application class (or `@EnableConfigurationProperties`).
- No `@Value("${...}")` sprinkled across classes (allowed only in a `@Configuration` class for a single simple value, and even then prefer properties records).
- Use nested records for grouped settings; `Duration`/`DataSize` types for durations/sizes (Boot converts `30s`, `10MB`); defaults with `@DefaultValue`.
- Add `spring-boot-configuration-processor` so IDEs get metadata; document each property with JavaDoc in pt-BR.
- Secrets never live in `application.yaml`; they come from environment variables / secret managers and are referenced as `${DB_PASSWORD}`.

```java
/**
 * Configurações do gateway de pagamento (prefixo {@code app.payment}).
 *
 * @param baseUrl URL base do gateway
 * @param timeout timeout total por chamada
 * @param retry política de retentativa
 */
@Validated
@ConfigurationProperties(prefix = "app.payment")
public record PaymentGatewayProperties(
        @NotBlank String baseUrl,
        @DefaultValue("5s") Duration timeout,
        @Valid Retry retry) {

    /** Retentativas: quantidade máxima e intervalo inicial (backoff exponencial). */
    public record Retry(@Min(0) @Max(5) @DefaultValue("3") int maxAttempts,
                        @DefaultValue("200ms") Duration initialBackoff) {}
}
```

### 5.6 DTOs, entities and mapping

- **Entities never leave the service layer.** Controllers receive `XxxRequest` records and return `XxxResponse` records. Entities are never serialized (avoids lazy-loading exceptions, infinite recursion, leaking columns, breaking the API when the schema changes).
- Request DTOs carry only what the client may set. **Never bind a request directly to an entity** (mass assignment). Never accept `id`, `version`, `createdAt`, `ownerId`, `role` from the client unless that is the explicit contract.
- Response DTOs are shaped for the client, not for the table. Aggregate/flatten as the use case needs. Separate `XxxSummaryResponse` (lists) from `XxxResponse` (detail).
- Mapping strategy, in order of preference:
  1. **JPQL constructor/`record` projections** for read models (`select new com.acme...InvoiceSummaryResponse(i.id, i.number, ...)` or Spring Data interface/record projections) — no mapping code, no entity loading.
  2. **Hand-written mapper** (a small `final class InvoiceMapper` with static or bean methods) when there are few fields — records make this trivial.
  3. **MapStruct** when the project has many mappings with nested objects; use `@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)` so unmapped fields fail the build.
- Never use reflection-based runtime mappers (ModelMapper, Dozer).
- Mapping from request → entity is not "mapping": it is a domain operation (`Invoice.create(request.customerId(), ...)`, `invoice.update(...)`). Keep invariants in the aggregate.

### 5.7 Domain model guidance

- Aggregates enforce their invariants through methods (`order.addItem(...)`, `order.cancel(reason)`), not through public setters. Setters exist only where any value is valid and no rule depends on the change.
- Value objects can be records (`Money`, `Cpf`, `Email`, `Address`). Jakarta Persistence 3.2 explicitly allows `@Embeddable` records and exempts them from the no-arg constructor requirement. Record embeddables in an older JPA baseline may depend on Hibernate-specific support (Hibernate 6.2+); distinguish the specification version from the provider version. Use `@AttributeOverride` for columns and `AttributeConverter` for single-column value objects.
- State transitions: an enum + a method per transition with a precondition check; optionally a `sealed` state model for complex workflows.
- Domain events: records published from the service after the aggregate method (or collected in the aggregate and published by the service). Listeners run after commit (`@TransactionalEventListener(phase = AFTER_COMMIT)`) and must be idempotent; for cross-service reliability use the **transactional outbox** (Spring Modulith event publication registry implements it).
- Anemic vs rich: put rules that depend only on the aggregate's own data in the aggregate; put rules that need repositories/external data in the application service or a domain service. Do not force every rule into entities, and do not put every rule into services.

### 5.8 Cross-cutting concerns

- Auditing: `@CreatedDate`, `@LastModifiedDate`, `@CreatedBy`, `@LastModifiedBy` via Spring Data JPA auditing (`@EnableJpaAuditing`, `AuditorAware<String>` from the security context) on a `@MappedSuperclass AuditableEntity`.
- Request correlation: accept/generate `X-Request-Id` in a filter, put it in MDC and the response header; with `spring-boot-starter-opentelemetry` traces carry `traceId`/`spanId` automatically into logs.
- Idempotency for POST: Part 6.10.
- Caching: `@Cacheable` only on read-heavy, rarely changing lookups with an explicit TTL (Caffeine locally, Redis when distributed). Never cache entities; cache DTOs/records. Evict on writes (`@CacheEvict`).
- Multitenancy/feature flags: only when required by the task; ask before adding.

---

## Part 6 — REST API design

### 6.1 Resources and URLs

- Plural nouns, kebab-case, lowercase: `/api/orders`, `/api/order-items`. No verbs in paths.
- Identifiers as path variables: `/api/orders/{orderId}`. Filters, sorting, pagination as query parameters.
- Nesting only for true ownership, max 2 levels: `/api/orders/{orderId}/items`. Deeper: promote to a root resource with a filter (`/api/order-items?orderId=`).
- Actions that are not CRUD are **sub-resources named as nouns or state transitions, invoked with POST**: `POST /api/orders/{id}/cancellation`, `POST /api/invoices/{id}/issuance`, `POST /api/payments/{id}/refunds`. Return `200` with the updated resource or `204`. Alternative for state changes: `PATCH /api/orders/{id}` with `{"status": "CANCELLED"}` when the transition needs no extra data — pick one style per project.
- For sensitive identifiers such as CPF, prefer a body-based search endpoint when required and ensure payload logging is disabled/redacted. Both URL path segments and query strings can appear in access logs, telemetry and browser history; TLS does not prevent those endpoint-side disclosures.
- Version prefix: use Spring's native API versioning (6.9) instead of hardcoding `/v1` in every mapping. If the team standardizes on path versions, use `spring.mvc.apiversion.use.path-segment` so mappings stay clean.
- No trailing slashes; no file extensions; no case-sensitive variants.

### 6.2 HTTP methods and status codes

Use the semantically correct method. Do not tunnel everything through POST unless the deployment environment demonstrably blocks other methods (and if it does, document it as a project-level exception at the top of the API config).

| Operation | Method | Success | Body | Idempotent |
|---|---|---|---|---|
| Create | `POST /resources` | `201 Created` + `Location` header + created representation | request DTO | No (use `Idempotency-Key`, 6.10) |
| Read one | `GET /resources/{id}` | `200` | — | Yes |
| List/search | `GET /resources?...` | `200` (empty page is still 200) | — | Yes |
| Complex search | `POST /resources/search` | `200` | filter DTO | Yes by contract (safe) |
| Full replace | `PUT /resources/{id}` | `200` (or `204`) | full DTO | Yes |
| Partial update | `PATCH /resources/{id}` | `200` | partial DTO (`application/merge-patch+json` or a dedicated record with nullable fields) | Yes by contract |
| Delete | `DELETE /resources/{id}` | `204` (`404` if absent; `204` also acceptable for repeat deletes — choose one) | — | Yes |
| State transition | `POST /resources/{id}/{transition}` | `200` with resource, or `204` | optional DTO | No unless keyed |
| Long-running | `POST /jobs` → `202 Accepted` + `Location: /jobs/{id}`; `GET /jobs/{id}` returns status | `202` | — | — |
| Upload | `POST /resources/{id}/attachments` (multipart) | `201` | multipart | No |
| Download | `GET /resources/{id}/pdf` | `200` + `Content-Disposition` | — | Yes |

Error statuses:

| Status | When |
|---|---|
| `400 Bad Request` | Malformed JSON, type mismatch, missing required parameter, constraint violation on parameters/body (default for Bean Validation in Spring; some teams prefer `422` for body validation — choose one and document it). |
| `401 Unauthorized` | Not authenticated (missing/invalid token). |
| `403 Forbidden` | Authenticated but not allowed (role/ownership). Do not leak existence: for ownership violations on GET by id, returning `404` is acceptable and often preferable. |
| `404 Not Found` | Resource does not exist. |
| `405 Method Not Allowed` | Framework-handled. |
| `406 / 415` | Content negotiation failures (framework-handled). |
| `409 Conflict` | Uniqueness violation (duplicate CPF), optimistic lock failure, invalid state transition (`order already cancelled`). |
| `412 Precondition Failed` | `If-Match` ETag mismatch. |
| `413 / 429` | Payload too large / rate limited (`Retry-After`). |
| `422 Unprocessable Content` | Semantically invalid business request (well-formed but violates a business rule that is not a state conflict). Only if the project adopts it. |
| `500` | Unexpected. Never for expected business failures. Body must not contain stack traces. |
| `503` | Downstream unavailable (circuit open). Include `Retry-After` when known. |

### 6.3 Controllers

- `@RestController` + `@RequestMapping("/api/orders")` at class level; `@GetMapping`, `@PostMapping`, etc. with `produces`/`consumes` only when not JSON.
- Return `ResponseEntity<T>` when you need to set status/headers explicitly (201 + Location, 204, downloads); return the DTO directly for plain 200 responses.
- One service call per endpoint. No `try/catch` in controllers (the advice maps exceptions). No business conditionals.
- `@Valid` on `@RequestBody`; constraint annotations directly on `@PathVariable`/`@RequestParam` (Spring 6.1+ method validation for controllers is built in — `@Validated` on the class is **not** required; violations become `HandlerMethodValidationException` → 400).
- Pagination parameters via `Pageable` with `@PageableDefault(size = 20, sort = "createdAt", direction = DESC)` and `@ParameterObject` (springdoc) for documentation.
- The authenticated principal via `@AuthenticationPrincipal` or a custom `@CurrentUser` meta-annotation; never read `SecurityContextHolder` inside services for authorization (use method security).
- Controllers are package-private classes; endpoint methods are public.
- Do not expose entity IDs from other aggregates as nested objects; use ids + links or embed a summary record.

### 6.4 Request/response DTOs

- Records (Part 3.1). Requests: constraints on components; nested DTOs annotated `@Valid`; collections `@NotEmpty List<@Valid ItemRequest>`; enums typed (not strings) so invalid values fail at binding.
- Responses: never `Map<String,Object>`; include `id`, timestamps as ISO-8601 `Instant`, money as string or `BigDecimal` (document which; `BigDecimal` serializes as a JSON number — enable `StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN` through a `JsonMapperBuilderCustomizer` to avoid scientific notation), enums as strings.
- Null handling: omit nulls by default (`spring.jackson.default-property-inclusion=non_null`) unless the contract requires explicit nulls (PATCH semantics).
- Naming in JSON: `camelCase` (Boot default). Never `snake_case` unless integrating with a system that requires it (then set `PropertyNamingStrategies.SNAKE_CASE` on the `JsonMapper`, project-wide, not per DTO).
- Keep DTOs free of persistence annotations and of Lombok.

### 6.5 Validation (Jakarta Validation 3.1 / Hibernate Validator 9)

- Structural validation (required, size, format, range) → Bean Validation on DTOs. Business validation (uniqueness, state, permissions) → service/domain, raising domain exceptions.
- Standard constraints: `@NotNull`, `@NotBlank`, `@NotEmpty`, `@Size`, `@Min/@Max`, `@Positive`, `@Past/@Future`, `@Email`, `@Pattern` (anchor regexes), `@Digits`, `@DecimalMin`. Use `@NotBlank` for strings, `@NotNull` for objects, `@NotEmpty` for collections.
- Custom constraints for domain formats: `@Cpf`, `@Cnpj`, `@ValidDateRange`. Hibernate Validator provides Brazilian CPF/CNPJ annotations, but verify that the installed CNPJ implementation supports the required alphanumeric format; older numeric-only validation is insufficient. Prove support with official valid/invalid numeric and alphanumeric test vectors before reusing it.
- Validation groups only when the same DTO is genuinely reused for create/update; prefer separate records.
- Messages: `{app.validation.cpf.invalid}` keys resolved from `ValidationMessages.properties` (pt-BR default). Include field-level errors in the Problem Details `errors` array (6.6).
- Fail fast: `hibernate.validator.fail_fast=false` (default) so the client gets all violations at once.
- Never validate by hand what an annotation can express; never duplicate DTO validation inside the service.

### 6.6 Error handling: RFC 9457 Problem Details

All error responses use `application/problem+json` with Spring's `ProblemDetail`. One `@RestControllerAdvice` extending `ResponseEntityExceptionHandler` maps everything; controllers and services never build error bodies.

Canonical shape:

```json
{
  "type": "https://api.acme.com/problems/invoice-already-issued",
  "title": "Fatura já emitida",
  "status": 409,
  "detail": "A fatura 2026-000123 já foi emitida em 2026-09-10T14:30:00Z.",
  "instance": "/api/invoices/8f1c.../issuance",
  "code": "INVOICE_ALREADY_ISSUED",
  "timestamp": "2026-09-10T14:31:02.123Z",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "errors": [ { "field": "items[0].quantity", "message": "deve ser maior que 0", "rejectedValue": 0 } ]
}
```

Rules:
- `type` is a stable URI per error code (documented), `title` is short and localized, `detail` is human-readable and localized, `instance` is the request path. Extra properties (`code`, `timestamp`, `traceId`, `errors`) via `problemDetail.setProperty(...)`.
- Map: `MethodArgumentNotValidException` / `HandlerMethodValidationException` / `ConstraintViolationException` → 400 with `errors`; `HttpMessageNotReadableException` → 400; `ResourceNotFoundException` → 404; `BusinessRuleException` / `InvalidStateException` / `DataIntegrityViolationException` (unique) / `OptimisticLockingFailureException` → 409; `AccessDeniedException` → 403 (usually handled by Spring Security before the advice); `Exception` → 500 with a generic detail and the full stack trace logged at ERROR with the `traceId`.
- Never expose exception class names, SQL, stack traces, or internal ids in `detail`.
- `spring.mvc.problemdetails.enabled=true` makes Spring's own MVC errors Problem Details when you do **not** define your own `ResponseEntityExceptionHandler`; if you extend it (recommended — full control), the property is redundant but harmless.
- Log at WARN for 4xx caused by clients (without stack trace, one line, with code + path), at ERROR for 5xx (with stack trace). Do not log validation errors as ERROR.
- Spring Security failures (401/403) are produced by `AuthenticationEntryPoint`/`AccessDeniedHandler`; configure them to emit the same Problem Details shape (Part 8).

### 6.7 Pagination and sorting

- Every collection endpoint that can grow is paginated. Accept `Pageable` (`page`, `size`, `sort=field,asc`), cap `size` (`spring.data.web.pageable.max-page-size=100`), default sort by a deterministic, indexed column (`createdAt DESC, id DESC` — always add a tiebreaker).
- **Never expose `Page<T>` directly**: its JSON is unstable across versions. Return `PagedModel<T>` (`spring.data.web.pageable.serialization-mode=via_dto`, Spring Data 3.3+) or a project `PageResponse<T>` record (`content`, `page`, `size`, `totalElements`, `totalPages`).
- Use `Slice<T>` (no count query) for infinite scroll; `Page<T>` when totals are required. Count queries on large tables are expensive — consider `Slice` or a cached approximate count.
- **Keyset (seek) pagination** for deep pages or high-volume feeds: Spring Data `Window<T>` + `ScrollPosition.keyset()` (`findFirst20ByStatusOrderByCreatedAtDescIdDesc(ScrollPosition)`); expose an opaque `cursor` in the API.
- Sorting: whitelist sortable properties (map API names → entity paths); reject unknown ones with 400. Never pass raw sort strings to JPQL.
- In-memory pagination (`subList`, `skip/limit` on a full list) is forbidden. Fetch-join + pagination pitfall: Part 7.6.

### 6.8 Filtering and search

- Simple filters: `@RequestParam` typed values → Spring Data `Specification` (JPA Criteria) composed with `Specification.where(...).and(...)`; `Specification.unrestricted()` (Spring Data 4) as the neutral element instead of `null`.
- Many filters / nested criteria: `POST /resources/search` with a `SearchXxxRequest` record → `Specification` or QueryDSL.
- Full-text: PostgreSQL `tsvector` or an external engine; do not `LIKE '%term%'` on large tables.
- Never build JPQL by string concatenation with user input. Parameters always bound (`:param`).

### 6.9 API versioning (Spring Framework 7 native)

- Configure one strategy per project in `application.yaml` (`spring.mvc.apiversion.use.header=X-API-Version` **or** `use.path-segment=1` **or** `use.query-parameter=version` **or** `use.media-type-parameter[application/json]=version`); set `spring.mvc.apiversion.supported` and `spring.mvc.apiversion.default`. Java equivalent: `WebMvcConfigurer#configureApiVersioning(ApiVersionConfigurer)`.
- Map versions on handlers: `@GetMapping(path = "/{id}", version = "1.0")` and `version = "2.0"` on a sibling method; unversioned handlers serve all versions. Use `version = "1.1+"` style baselines where supported.
- Deprecate versions with an `ApiVersionDeprecationHandler` (`StandardApiVersionDeprecationHandler`) so responses carry RFC 9745 `Deprecation`/`Sunset` headers.
- Prefer additive, backward-compatible changes (new optional fields) over new versions. Bump the version only for breaking changes.
- Clients: `RestClient`/HTTP interfaces support `.apiVersion(...)`; tests: `MockMvcTester`/`RestTestClient` support `apiVersion` too.

### 6.10 Idempotency, concurrency control, caching headers

- **`Idempotency-Key`** header on POST create/transition endpoints that clients may retry (payments, orders): store `(key, requestHash, responseStatus, responseBody, expiresAt)` in a table with a unique constraint; replay the stored response for the same key + same body; `409`/`422` for same key + different body. Scope keys per authenticated client.
- **Optimistic locking**: entities have `@Version`; updates carry the version (as `ETag`/`If-Match` or a `version` field in the DTO). `OptimisticLockingFailureException` → `409` (or `412` when using `If-Match`).
- **`ETag` / `If-None-Match`** on GET of single resources (`ShallowEtagHeaderFilter` for cheap wins, or version-based ETags computed from `@Version`). `Cache-Control: no-store` for personal data; `private, max-age=...` for cacheable user data; `public, max-age=..., immutable` for static catalogs.
- Retries from clients must be safe: PUT/DELETE idempotent by design; POST guarded by keys.

### 6.11 File upload and download

- Upload: `consumes = MULTIPART_FORM_DATA_VALUE`, `@RequestPart MultipartFile file` (+ `@RequestPart @Valid Metadata metadata` for JSON parts). Validate size (`spring.servlet.multipart.max-file-size`), MIME type by **content sniffing** (Apache Tika) not by extension, filename sanitized (never used as a path), virus scan if required. Store in object storage (S3-compatible) — never in the database as `bytea` for large files, never on the local filesystem of a container.
- Download: `ResponseEntity<Resource>` (`InputStreamResource`/`ByteArrayResource`) with `Content-Type`, `Content-Disposition` (`ContentDisposition.attachment().filename(name, UTF_8).build()`), `Content-Length` when known; `StreamingResponseBody` for large generated files. Prefer pre-signed URLs from object storage for large downloads.
- Generation with side effects (persist, audit, status change) is POST, not GET.

### 6.12 OpenAPI documentation (springdoc-openapi 3.x)

- `springdoc-openapi-starter-webmvc-ui` 3.x; UI at `/swagger-ui.html`, spec at `/v3/api-docs`. Disable the UI in production (`springdoc.swagger-ui.enabled=false`) or protect it.
- Global `OpenAPI` bean with title, version, contact, security scheme (`bearerAuth`). Group by feature with `GroupedOpenApi` if the API is large.
- Annotate controllers minimally: `@Tag` on the class, `@Operation(summary=...)` on methods, `@ApiResponse` only for non-obvious statuses; document parameters via Bean Validation + `@Schema(description=...)` on DTO components (pt-BR descriptions are fine here — they are user-facing docs). Do not duplicate what springdoc infers.
- `@ParameterObject` on `Pageable`/search records. Register `ProblemDetail` as the error schema.
- The spec is a contract: consider generating clients from it and running a diff in CI to catch breaking changes.

### 6.13 JSON / Jackson 3 conventions

- Boot 4 auto-configures `tools.jackson.databind.json.JsonMapper`. Customize globally with a `JsonMapperBuilderCustomizer` bean or properties (`spring.jackson.*`), never per-controller.
- Recommended settings: `spring.jackson.default-property-inclusion=non_null`; `spring.jackson.deserialization.fail-on-unknown-properties` — Jackson 3 changed the default to `false` (lenient input); set `true` for strict contracts — decide per project; dates as ISO-8601 strings (Jackson 3 default: `WRITE_DATES_AS_TIMESTAMPS` is off); `spring.jackson.time-zone=UTC`.
- Annotations still come from `com.fasterxml.jackson.annotation` (`@JsonProperty`, `@JsonIgnore`, `@JsonFormat`, `@JsonTypeInfo`, `@JsonSubTypes`, `@JsonView`, `@JsonInclude`).
- Polymorphic payloads: sealed interface + `@JsonTypeInfo(use = NAME, property = "type")` + `@JsonSubTypes`; never `@JsonTypeInfo(use = CLASS)` (deserialization gadget risk).
- Custom serializers/deserializers: `@JacksonComponent` classes extending `ObjectValueSerializer`/`ObjectValueDeserializer`; only for real needs (e.g., masking CPF in output).
- Jackson 2 (`com.fasterxml.jackson.databind`) is deprecated in Boot 4 — do not add it; if a library requires it, isolate it (`spring-boot-jackson2` module) and plan the removal.

### 6.14 Calling other HTTP services

- **`RestClient`** (synchronous, fluent) built from the auto-configured `RestClient.Builder`; or **HTTP Service interfaces** (`@HttpExchange`/`@GetExchange` on an interface) registered with `@ImportHttpServices(group = "payment", types = PaymentGatewayClient.class)` and configured via `spring.http.serviceclient.payment.base-url`, `.connect-timeout`, `.read-timeout`. This is the Spring 7 / Boot 4 standard.
- `RestTemplate` is deprecated; `WebClient` only in reactive apps (or for streaming), and then with `spring-boot-starter-webclient`.
- Always: timeouts, `ErrorHandler`/`onStatus` mapping to domain exceptions, retries with backoff only for idempotent calls (`@Retryable`, Part 12), circuit breaker for critical dependencies, observation (`RestClient` is auto-instrumented with Micrometer), no credentials in URLs, TLS via SSL bundles (`spring.ssl.bundle.*`).
- Wrap the client in a feature-local adapter (`PaymentGateway` interface + `RestPaymentGateway` implementation) so the domain does not depend on HTTP types and tests can stub it.

### 6.15 CORS, headers, content negotiation

- CORS configured once (`CorsConfigurationSource` bean used by Spring Security), with explicit origins (never `*` with credentials), methods and headers; `maxAge` for preflight.
- Security headers are added by Spring Security (`X-Content-Type-Options`, `Cache-Control`, `HSTS` when HTTPS). Add `Content-Security-Policy` for any HTML.
- Only JSON (and multipart/octet-stream where needed). No XML unless required. No suffix-based content negotiation (removed in Spring 7).
- `X-Request-Id` in/out; `traceparent` propagated by OpenTelemetry.
- Compression on (`server.compression.enabled=true`, `server.compression.additional-mime-types` for problem+json). HTTP/2 on (`server.http2.enabled=true`) when TLS terminates at the app.
- Graceful shutdown (`server.shutdown=graceful`, `spring.lifecycle.timeout-per-shutdown-phase=30s`).

### 6.16 Async and long-running operations

- Never block a request for minutes. Accept with `202` + a job resource; process with `@Async` (virtual threads) or a queue; expose status (`GET /jobs/{id}`) and completion via polling or webhooks/SSE.
- SSE via `SseEmitter`/`ResponseBodyEmitter` for progress streams; WebSocket only for bidirectional needs.

---

## Part 7 — Persistence (Spring Data JPA 4 / Hibernate ORM 7 / PostgreSQL)

### 7.1 Entity rules

- `@Entity` + `@Table(name = "orders")` (explicit, plural, snake_case). Explicit `@Column(name = ..., nullable = ..., length = ..., precision/scale = ...)` for every column so the mapping is self-documenting and matches the Flyway DDL.
- Lombok: `@Getter`, `@NoArgsConstructor(access = PROTECTED)`, selective `@Setter`, `@ToString(onlyExplicitlyIncluded = true)`. **Never** `@Data`, `@EqualsAndHashCode`, class-level `@Setter`, `@Builder` as the only creation path.
- Creation through a **static factory** (`Order.create(customer, items)`) or a constructor that enforces invariants; state changes through **behavior methods**.
- `equals`/`hashCode` for entities with generated ids:

```java
// Igualdade por identidade de persistência: só é possível comparar depois de o id ser atribuído.
// hashCode constante por classe mantém o contrato mesmo quando o id é gerado após inserir em um Set.
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Order other)) return false;      // 'instanceof' cobre proxies do Hibernate
    return id != null && id.equals(other.getId());       // getId(): passa pelo proxy, não pelo campo
}

@Override
public int hashCode() {
    return Order.class.hashCode();
}
```
  With client-assigned UUID v7 (assigned before persist) you may compare by id unconditionally; still use `getId()` and `instanceof`.
- Never call lazy associations in `toString`, `equals`, `hashCode`, or logging.
- Do not put `final` on entity classes or persistent methods (Hibernate proxies). Fields are `private`.
- Keep entities free of web/JSON annotations.
- Use a `@MappedSuperclass AuditableEntity` (createdAt/updatedAt/createdBy/updatedBy) and, if needed, `AbstractEntity<ID>`.

### 7.2 Identifiers

- **Default: `UUID` v7** — `@Id @GeneratedValue @UuidGenerator(style = UuidGenerator.Style.VERSION_7) private UUID id;` (Hibernate 7). Column `uuid`. Time-ordered → index-friendly; safe to expose; assigned before insert → batching and `equals` are simpler.
- Alternative when internal joins dominate and IDs are never exposed: `Long` with `@GeneratedValue(strategy = SEQUENCE, generator = "orders_seq") @SequenceGenerator(name = "orders_seq", sequenceName = "orders_seq", allocationSize = 50)`. Match `allocationSize` with the DB sequence `INCREMENT BY`.
- **Never `IDENTITY`** (disables JDBC batching, forces a round-trip per insert). Never `AUTO` (implicit behavior). Never natural keys (CPF, e-mail) as primary keys — use unique constraints instead.
- Composite keys only for pure join/history tables; prefer a surrogate id + unique constraint.

### 7.3 Associations and fetching

- **Every association is `LAZY`**: `@ManyToOne(fetch = LAZY)`, `@OneToOne(fetch = LAZY)` (note: `@OneToOne` on the non-owning side cannot be lazy without bytecode enhancement — prefer unidirectional from the owning side or `@MapsId`), `@OneToMany`/`@ManyToMany` are lazy by default.
- Prefer **unidirectional `@ManyToOne`** from the child. Add the `@OneToMany(mappedBy = ...)` side only when the aggregate genuinely manages the children (`cascade = ALL, orphanRemoval = true`), and keep the collection `List` for ordered (`@OrderColumn`) or `Set` for unordered children; expose it read-only.
- Avoid `@ManyToMany`; model the join table as an entity when it has (or will have) attributes.
- `CascadeType.ALL` only inside an aggregate (parent → owned children). Never cascade across aggregates. Never `CascadeType.REMOVE` on `@ManyToOne`.
- Fetching a graph for a use case: `@EntityGraph(attributePaths = {"customer", "items"})` on the repository method or a `join fetch` in JPQL. `@BatchSize(size = 50)` on collections (or `spring.jpa.properties.hibernate.default_batch_fetch_size=50`) as a global safety net against N+1.
- Detect N+1 in tests: `spring.jpa.show-sql=false` always; use Hibernate statistics (`hibernate.generate_statistics=true` in tests + assert query count) or a datasource proxy in integration tests when a query is performance-sensitive.
- **`spring.jpa.open-in-view=false`, always.** OSIV hides lazy-loading bugs, holds connections during view rendering/serialization, and breaks under virtual threads' connection pressure. Load what the use case needs inside the transaction and map to DTOs there.

### 7.4 Transactions

- `@Transactional` on **service** public methods (never on controllers or repositories — Spring Data repositories are already transactional per method). Class-level `@Transactional(readOnly = true)` + method-level `@Transactional` on write methods is the cleanest arrangement.
- `readOnly = true` for reads: Hibernate skips dirty checking/flush, driver may route to replicas.
- Boundaries: one use case = one transaction. Do not call external HTTP/messaging inside a transaction (long locks, inconsistency on failure) — do it after commit (`@TransactionalEventListener(AFTER_COMMIT)` or `TransactionSynchronization`).
- Self-invocation does not go through the proxy: a `@Transactional` method called from another method of the same bean runs in the caller's context. Restructure (extract a bean) instead of self-injecting.
- Rollback: unchecked exceptions roll back; checked do not unless `rollbackFor = Exception.class`. Domain exceptions are unchecked (Part 3.9), so defaults work.
- A **transactional outbox is written in the same transaction as the business change**: either both commit or neither does. Do not use `REQUIRES_NEW` for this outbox. A separately justified audit of failed attempts may use an independent transaction; document its semantics and extra connection demand.
- `TransactionTemplate` for programmatic control (batch loops committing every N items).
- Never catch and swallow exceptions inside a transactional method and then continue writing: the transaction is marked rollback-only and commit throws `UnexpectedRollbackException`.
- Set `spring.transaction.default-timeout` (e.g., 30s) and `@Transactional(timeout = ...)` for batch operations.

### 7.5 Repositories and queries

- Interface per aggregate root: `interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order>` (package-private when possible). No repositories for non-root entities.
- Query styles, in order of preference:
  1. **Derived query methods** for simple lookups (`findByCustomerIdAndStatus`, `existsByNumber`). Spring Data 4 renders them as JPQL (cached query plans). Keep names short; beyond ~3 conditions use `@Query`.
  2. **`@Query` with JPQL in a text block** and named parameters. `@Modifying(clearAutomatically = true, flushAutomatically = true)` for bulk updates.
  3. **Projections** for read models: interface projections (`interface OrderSummary { UUID getId(); String getNumber(); }`) or, preferred, **record/DTO projections** via constructor expression (`select new com.acme.billing.order.dto.OrderSummaryResponse(o.id, o.number, c.name) from Order o join o.customer c`) or Spring Data's DTO derivation (`List<OrderSummaryResponse> findByStatus(OrderStatus status)` — Spring Data 3.5+ derives the constructor expression).
  4. **`Specification`** for dynamic filters (`JpaSpecificationExecutor`); compose small named specs in `OrderSpecifications`.
  5. **Native SQL** (`nativeQuery = true`) only for database-specific features (CTEs, window functions, `jsonb`, `FOR UPDATE SKIP LOCKED`); map to records/interfaces, never to entities unless required.
  6. `JdbcClient` (Spring 6.1+) for reporting/bulk queries that do not need the persistence context; `Spring Data JDBC` as an alternative for simple aggregates without lazy loading.
- Return types: `Optional<T>` for single, `List<T>` only for bounded results, `Page<T>`/`Slice<T>`/`Window<T>` for unbounded, `Stream<T>` (inside a transaction, try-with-resources) for large exports, `boolean exists...`, `long count...`.
- Never `findAll()` on unbounded tables; never load entities to compute aggregates (use `count`/`sum` queries).
- Use the JPA static metamodel (`Order_.status`) via `hibernate-processor` in Specifications and Criteria for type safety.
- `@Query` strings are constants: no concatenation, ever.

### 7.6 Pagination pitfalls (must read)

- `join fetch` a collection **plus** `Pageable` → Hibernate applies pagination **in memory** (`HHH90003004`/firstResult-maxResults-in-memory warning) and loads the whole result set. Never do it. Paginate ids first (`Page<UUID>`), then load with `@EntityGraph`/`join fetch` by `id in :ids`, preserving order; or use `@BatchSize`; or project to DTOs.
- A collection fetch join multiplies SQL rows. Hibernate 6+ automatically removes duplicate root entity references in these results. An explicit JPQL `distinct` is passed through to SQL; add it only when query semantics require it, not as a routine workaround for entity duplication. Changing a collection to `Set` does not repair SQL pagination or cardinality.
- `Page` executes a count query; with `join fetch` the count must be derived — provide `countQuery` on `@Query` when Spring Data cannot derive it.
- Sorting on a joined property requires the join to exist in the query.
- Default page size/max are configured globally (6.7).

### 7.7 Schema migrations — Flyway (mandatory)

- `spring-boot-starter-flyway` + `flyway-database-postgresql`. `spring.jpa.hibernate.ddl-auto=validate` in **every** environment (Hibernate checks the mapping against the migrated schema at startup). Never `update`/`create`/`create-drop`, not even in tests (tests run migrations too — that is how migrations get tested).
- Files in `src/main/resources/db/migration/V<yyyyMMddHHmm>__<description>.sql`; repeatable scripts `R__<description>.sql` for views/functions. One concern per migration. Never edit an applied migration; add a new one.
- Backward-compatible migrations for zero-downtime deploys (expand → migrate data → contract): add nullable column → backfill → add constraint; rename via add-new + copy + drop-old in later releases.
- Explicit DDL: types (`uuid`, `timestamptz`, `numeric(19,4)`, bounded text), nullability, defaults and named constraints. Choose indexes from actual predicates, ordering, joins and plans; inspect FK lookup needs and existing composite indexes before adding redundant ones. For PostgreSQL `CREATE INDEX CONCURRENTLY`, put `executeInTransaction=false` in the matching sidecar file, e.g. `V202609131200__orders_index.sql.conf` beside `V202609131200__orders_index.sql`. An inline `-- flyway:...` SQL comment is not this configuration mechanism. Plan failed concurrent-index cleanup and use the Flyway version's documented transaction settings.
- Seed/reference data via migrations (`V..._seed_order_statuses.sql`), never via `data.sql` or code on startup. Test fixtures via `@Sql` or builders, not via migrations.
- Generate the initial DDL from Hibernate (`spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=create`) once, then **review and hand-edit** it — never ship generated DDL blindly.
- Spring Modulith: per-module migration locations if using modules.

### 7.8 Configuration

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 10          # dimensionar pelo banco, não pelo número de threads (virtual threads!)
      minimum-idle: 2
      connection-timeout: 3000       # falhar rápido sob contenção
      max-lifetime: 1800000
      auto-commit: false             # Hibernate gerencia; evita round-trips (hibernate.connection.provider_disables_autocommit)
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        default_batch_fetch_size: 50
        jdbc.batch_size: 50
        order_inserts: true
        order_updates: true
        connection.provider_disables_autocommit: true
        query.fail_on_pagination_over_collection_fetch: true   # transforma o pitfall 7.6 em erro
        query.in_clause_parameter_padding: true
  flyway:
    locations: classpath:db/migration
```

- Batching requires `SEQUENCE`/UUID ids (not `IDENTITY`) and `order_inserts/updates`.
- Statement logging: `logging.level.org.hibernate.SQL=DEBUG` and `org.hibernate.orm.jdbc.bind=TRACE` only in `local` profile; never `show-sql`.
- Slow query log: `spring.jpa.properties.hibernate.session.events.log.LOG_QUERIES_SLOWER_THAN_MS=200` in non-prod.

### 7.9 Auditing

- `@EnableJpaAuditing(auditorAwareRef = "auditorAware")`; `AuditorAware<String>` reads the JWT subject; `@CreatedDate Instant createdAt`, `@LastModifiedDate Instant updatedAt`, `@CreatedBy String createdBy`, `@LastModifiedBy String updatedBy` on `AuditableEntity` with `@EntityListeners(AuditingEntityListener.class)`. Or Hibernate `@CreationTimestamp`/`@UpdateTimestamp` when no "by" is needed.
- Full change history (who changed what): Hibernate Envers (`@Audited`) or an explicit history table written in the same transaction — decide per compliance need; do not add by default.

### 7.10 Soft delete

- Only when the business requires it (legal retention, undo). Hibernate `@SoftDelete(columnName = "deleted", strategy = SoftDeleteType.DELETED)` on the entity (Hibernate 6.4+): deletes become updates and queries filter automatically. Add partial unique indexes (`unique (cpf) where not deleted`). Remember soft-deleted rows still occupy uniqueness/space; plan purges.

### 7.11 Locking and concurrency

- `@Version private long version;` on every aggregate root that can be updated concurrently (optimistic locking, default choice). Map `OptimisticLockingFailureException`/`ObjectOptimisticLockingFailureException` → 409/412.
- Pessimistic locks for hot rows and counters: `@Lock(LockModeType.PESSIMISTIC_WRITE)` on a repository method + `@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))`; for work queues use native `select ... for update skip locked`.
- Retry optimistic conflicts at the service boundary with `@Retryable(includes = OptimisticLockingFailureException.class, maxRetries = 3)` (`org.springframework.resilience.annotation.Retryable`) **only** when the operation is idempotent per retry.
- Unique constraints in the database are the reliable duplicate guard; a check-then-insert alone is a race. Map known uniqueness violations to 409; other integrity failures need explicit classification, not a blanket duplicate response.

### 7.12 Types: enums, money, time, JSON, large values

- Enums: `@Enumerated(EnumType.STRING)` + `varchar(30)` or a PostgreSQL `enum`/check constraint. Never ordinal.
- Money: `BigDecimal` ↔ `numeric(19,4)` (or a `Money` embeddable with amount + currency).
- Time: `Instant` ↔ `timestamptz`; `LocalDate` ↔ `date`. Set `spring.jpa.properties.hibernate.jdbc.time_zone=UTC` and run the JVM in UTC (`-Duser.timezone=UTC`); convert to `America/Fortaleza` etc. only at the presentation layer.
- JSON columns: `@JdbcTypeCode(SqlTypes.JSON) private Map<String, Object> attributes;` or a record ↔ `jsonb` (Hibernate 6+ native JSON support). Do not use JSON columns to avoid modeling.
- Text: `text` for unbounded, `varchar(n)` with a business limit otherwise. `@Lob` only for real large objects; large binaries go to object storage (6.11).
- Arrays/`uuid[]`: Hibernate 6+ maps `List<UUID>`/`String[]` to PostgreSQL arrays with `@JdbcTypeCode(SqlTypes.ARRAY)`.

### 7.13 Testing persistence

- Repository/query tests: `@DataJpaTest` + Testcontainers PostgreSQL via `@ServiceConnection` (never H2 — dialect differences hide bugs). Flyway runs the migrations. Assert SQL behavior (filters, pagination, projections, constraints).
- Service tests with transactions: `@SpringBootTest` slices or full context with Testcontainers; verify rollback/commit semantics where they matter.
- Do not annotate integration tests with `@Transactional` to "auto-rollback" when testing transactional boundaries — it hides real commit behavior; clean up with `@Sql` or truncate in `@AfterEach`.

---

## Part 8 — Security (Spring Security 7)

### 8.1 Configuration principles

- **Deny by default across all requests.** Each `SecurityFilterChain` protects only matching requests. With multiple scoped chains, give them explicit `@Order` values and add a final chain with no `securityMatcher` and `anyRequest().denyAll()` (or the explicitly required web policy). A route that matches no chain is outside Spring Security protection. Enumerate public endpoints; test a route outside the API/Actuator matchers.
- Lambda DSL only (7.0 removed `.and()` and `authorizeRequests()`); `SecurityFilterChain` beans (`@Bean`), possibly several with `securityMatcher(...)` for different surfaces (API vs actuator vs web).
- Request matchers are `PathPatternRequestMatcher` by default (Ant/Mvc matchers were removed); patterns are absolute paths (`/api/**`), no context path.
- Stateless Bearer APIs: disable CSRF only when authentication never relies on credentials automatically attached by a browser (including cookies containing a JWT, session cookies, or HTTP Basic). `STATELESS` alone is not a CSRF defense. Keep CSRF protection for cookie-authenticated browser flows; configure CORS independently.
- Authentication for APIs: **OAuth2 Resource Server with JWT** (`spring-boot-starter-security-oauth2-resource-server`, `spring.security.oauth2.resourceserver.jwt.issuer-uri`), tokens issued by an authorization server (Keycloak, Spring Authorization Server — now part of Spring Security 7, Auth0, Cognito...). Do not hand-roll JWT issuance/validation with `jjwt`/`java-jwt` unless the task explicitly requires it; if you must, use short-lived access tokens, rotate keys (JWKS), validate `iss`/`aud`/`exp`/`nbf`, and never put secrets or PII in claims.
- Opaque tokens / API keys: `opaqueToken()` with introspection, or a custom `AuthenticationFilter` + `AuthenticationManager` for API keys stored hashed.
- Passkeys/WebAuthn and MFA "factors" are available in Spring Security 7 for browser apps; use the framework support, never custom crypto.

### 8.2 Authorization

- Coarse-grained by URL in the filter chain (`hasRole`, `hasAuthority`, `hasAnyAuthority("SCOPE_orders:write")`).
- Fine-grained with **method security** on services: `@EnableMethodSecurity` + `@PreAuthorize("hasAuthority('SCOPE_orders:write')")`, `@PreAuthorize("#customerId == authentication.name or hasRole('ADMIN')")`, `@PostAuthorize`/`@PostFilter` for ownership on returned objects, `@AuthorizeReturnObject` for domain object authorization when appropriate.
- **Ownership (IDOR) checks are mandatory** for any resource that belongs to a user/tenant: the query itself includes the owner (`findByIdAndCustomerId`) or the service verifies it — never trust that "the client will only ask for its own ids".
- Roles vs authorities: use fine-grained authorities/scopes (`orders:read`, `orders:write`); roles (`ROLE_ADMIN`) for broad personas. Map JWT claims with a `JwtAuthenticationConverter` (`JwtGrantedAuthoritiesConverter` with the right claim name and prefix) — keep the mapping in one class.
- `AuthorizationManager` API for custom decisions (voters/`AccessDecisionManager` are gone).

### 8.3 Passwords and secrets

- Store passwords only with `PasswordEncoder`: `PasswordEncoderFactories.createDelegatingPasswordEncoder()` (current default `{bcrypt}`, upgradeable) or explicitly `Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()` for new systems (OWASP-preferred). Never MD5/SHA-x, never plain, never reversible.
- Enforce minimum length (≥ 12) and check against breached passwords (`HaveIBeenPwnedRestApiPasswordChecker`) rather than complexity rules.
- Secrets (DB passwords, API keys, JWT keys) come from environment/secret manager; `application.yaml` references `${VAR}`; nothing secret in Git, logs, error messages, or `toString`. Rotate keys via SSL bundles / JWKS.
- Never log tokens, `Authorization` headers, cookies, CPF/CNPJ in full, card data (PCI). Mask in `toString`/serializers.

### 8.4 Input handling and injection

- SQL/JPQL: bound parameters only (Part 7.5). Native queries with dynamic table/column names must whitelist.
- Command execution, file paths, LDAP, XML (XXE: disable external entities), deserialization (`ObjectInputStream`), SpEL from user input, template injection: avoid or whitelist strictly.
- Validate everything at the edge (Bean Validation) **and** authorize in the service; validation ≠ authorization.
- Uploads: Part 6.11. Limits on request size (`server.max-http-request-header-size`, multipart limits), JSON depth (Jackson 3 defaults are stricter), pagination size.
- Enumeration/timing: uniform error messages for login; constant-time comparison for secrets (`MessageDigest.isEqual`).

### 8.5 Transport, headers, CORS, actuator

- HTTPS everywhere (TLS at the edge or `server.ssl.bundle`); HSTS on; `requiresChannel`/`redirectToHttps` when the app terminates TLS.
- Security headers are on by default (`X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Cache-Control: no-store`); add CSP for HTML; `Referrer-Policy: strict-origin-when-cross-origin`.
- CORS through Spring Security's `cors(Customizer.withDefaults())` with a `CorsConfigurationSource` bean: explicit origins, methods, headers, `allowCredentials` only with explicit origins, `exposedHeaders` for `Location`/`ETag`.
- Actuator: expose only `health` (and `info`, `prometheus` when scraped) over the public port; put management on a separate port (`management.server.port`) or protect with a dedicated `SecurityFilterChain` (`EndpointRequest.toAnyEndpoint()`); never expose `env`, `heapdump`, `threaddump`, `configprops` publicly; `management.endpoint.health.show-details=when_authorized`.
- Rate limiting at the gateway; in-app with Bucket4j (per user/API key) for sensitive endpoints (login, OTP, password reset); `@ConcurrencyLimit` (Spring 7) to protect expensive operations.

### 8.6 Error responses from security

- `401`/`403` bodies must be Problem Details like everything else: custom `AuthenticationEntryPoint` and `AccessDeniedHandler` writing `ProblemDetail` JSON via the auto-configured `JsonMapper`; no HTML, no default Basic-auth challenge on APIs (`WWW-Authenticate: Bearer` for JWT).
- Do not reveal whether a user exists; do not include stack traces.

### 8.7 Testing security

- `spring-boot-starter-security-test`: `@WithMockUser(authorities = "SCOPE_orders:read")`, `SecurityMockMvcRequestPostProcessors.jwt().authorities(...)` in `@WebMvcTest`/`MockMvcTester`; `@WithMockUser` for service method security tests (`@SpringBootTest` slice with `@EnableMethodSecurity`).
- Every protected endpoint has at least: anonymous → 401, wrong authority → 403, owner vs non-owner → 200/404-403.
- Do not disable security in tests with a permissive profile; test the real chain (`@WebMvcTest` includes `SecurityFilterChain` beans when they are in the slice — `@Import(SecurityConfig.class)`).

---

## Part 9 — Logging and observability

### 9.1 Logging API and configuration

- **SLF4J facade only** (`org.slf4j.Logger`), obtained via `@Slf4j` or `LoggerFactory.getLogger(Foo.class)`. Backend: Logback (Boot default). Never code against Log4j2/JUL APIs; never `System.out`/`printStackTrace`.
- Parameterized messages `log.info("Invoice issued invoiceId={} orderId={}", invoiceId, orderId)`; no string concatenation; no `isDebugEnabled()` guards unless the argument is expensive to compute (then use a `Supplier`/fluent API `log.atDebug().addArgument(() -> expensive()).log("...")`).
- Log exceptions with the throwable as the **last** argument: `log.error("Failed to issue invoice invoiceId={}", id, e)`. Log an exception **once**, at the boundary that handles it (controller advice, listener, scheduler). Rethrown exceptions are not logged.
- **Structured logging** (Boot 3.4+): `logging.structured.format.console=ecs` (or `logstash`, `gelf`) in non-local profiles so logs are JSON for the aggregator; human-readable pattern only in `local`. Add key-value context with SLF4J 2 fluent API (`log.atInfo().addKeyValue("orderId", id).log("Order created")`) or MDC.
- MDC keys set by filters/aspects: `requestId`, `userId` (subject, not e-mail/CPF), `tenantId`; `traceId`/`spanId` are injected automatically by Micrometer Tracing / OpenTelemetry. MDC is `ThreadLocal`-based: propagate to `@Async`/manual threads via a `TaskDecorator` or Micrometer Context Propagation; clear MDC in `finally`.
- Levels: `ERROR` = needs human action / lost work; `WARN` = unexpected but handled (retry, fallback, client 4xx worth noticing); `INFO` = business milestones (order created, payment approved, job finished) and lifecycle; `DEBUG` = technical detail for developers; `TRACE` = payload-level. No `FATAL` in SLF4J — use `ERROR` with a marker if you need to distinguish. Production default `INFO`, framework packages `WARN`, `org.hibernate.SQL` never above `DEBUG` outside local.
- **Never log**: passwords, tokens, `Authorization`/`Cookie` headers, full CPF/CNPJ/card numbers, full request/response bodies with personal data, secrets from config. Mask or hash if needed for correlation.
- Do not log inside tight loops; aggregate ("processed 10.000 rows in 3.2s").
- Log messages are English, stable, and searchable (fixed prefix + key=value pairs), not prose that changes per branch.

### 9.2 Actuator, metrics, tracing

- `spring-boot-starter-actuator` always. `management.endpoints.web.exposure.include=health,info,prometheus,metrics` (adjust), health groups `liveness`/`readiness` (enabled by default in Boot 4; used by Kubernetes probes), `management.endpoint.health.probes.add-additional-paths=true` to expose `/livez` and `/readyz` on the main port if needed.
- Custom `HealthIndicator`s for critical dependencies that are not auto-detected; keep them cheap and cached.
- Metrics via Micrometer: HTTP server/client, JDBC pool, JVM, logs are automatic. Add business counters/timers (`MeterRegistry` or `@Timed`/`@Counted` with `spring-boot-starter-aspectj`) with low-cardinality tags (never ids as tags). Expose Prometheus (`spring-boot-starter-micrometer-metrics` + `micrometer-registry-prometheus`) or OTLP.
- Tracing via `spring-boot-starter-opentelemetry` (OTLP exporter for traces and metrics) — Micrometer Observation instruments MVC, RestClient, JDBC (with the datasource observation), Kafka, etc. Annotate important service methods with `@Observed(name = "invoice.issue")` for spans. Sampling: `management.tracing.sampling.probability` (1.0 in dev, lower in prod).
- Correlation: `traceId` is included in Problem Details responses (6.6) and logs — support can jump from an error to the trace.
- `info` endpoint: git (`git-commit-id` plugin), build info (`spring-boot:build-info`), Java version. Useful for "what is deployed".
- Alerts are built on metrics/SLOs (error rate, p99 latency, pool exhaustion, GC pauses), not on log greps.

---

## Part 10 — Testing

### 10.1 Strategy

| Layer | Test type | Tooling | Speed | What it proves |
|---|---|---|---|---|
| Domain (entities, value objects, sealed types) | Pure unit test, no Spring | JUnit 6 + AssertJ | ms | Business rules, invariants, state transitions |
| Application service | Unit test with mocks | JUnit 6 + Mockito (`@ExtendWith(MockitoExtension.class)`) + AssertJ | ms | Orchestration, error mapping, event publication |
| Controller | Web slice | `@WebMvcTest` + `MockMvcTester` + `@MockitoBean` for the service + security test support | ~1s (context cached) | Mapping, validation, status codes, Problem Details, auth |
| Repository / queries | Data slice | `@DataJpaTest` + Testcontainers PostgreSQL (`@ServiceConnection`) + Flyway | seconds | SQL correctness, constraints, projections, pagination |
| End-to-end use case | Integration | `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `RestTestClient` + Testcontainers | seconds | Wiring, transactions, security chain, serialization |
| Architecture | ArchUnit | `archunit-junit6` for JUnit 6 | ms | Dependency rules, naming, no cycles |
| External HTTP dependencies | Contract/stub | WireMock (or `MockRestServiceServer` for `RestClient`) | ms–s | Client mapping, timeouts, error handling |
| Async/scheduled | Awaitility | `await().atMost(...).untilAsserted(...)` | s | Eventual outcomes without `Thread.sleep` |

Pyramid: many unit tests, a focused set of slice tests, few full integration tests per feature (happy path + the risky failure paths). Every bug fix gets a regression test that fails before the fix.

### 10.2 General rules

- JUnit 6 (Jupiter) only: `@Test`, `@ParameterizedTest` (`@CsvSource`, `@MethodSource`, `@EnumSource`), `@Nested` for grouping by scenario, `@DisplayName` in pt-BR, `@Tag("integration")` for slow tests (Failsafe runs `*IT`, Surefire runs `*Test`).
- Prefer AssertJ for Java/domain assertions. The classic `MockMvc` result-matcher DSL (including a Hamcrest matcher where its API requires one) is acceptable for HTTP assertions; keep style consistent within each suite.
- One behavior per test; name states the behavior (2.2). Arrange/Act/Assert with blank lines.
- Test data through **builders/object mothers** in `src/test/java/.../support` (`OrderTestData.pendingOrder()`, `CreateOrderRequestBuilder`) — never copy-paste 30-line fixtures. Fixed `Clock` for time. Fixed UUIDs/seeds for determinism.
- No logic in tests (no loops/ifs computing expectations); no shared mutable state between tests; no order dependence.
- Mocks only for collaborators outside the unit (repositories, clients, publishers); never mock value objects, records, or the class under test. Prefer fakes/in-memory implementations for ports when behavior matters.
- `@MockitoBean`/`@MockitoSpyBean` in Spring tests; `@Mock`/`@InjectMocks` with `MockitoExtension` in plain unit tests. Verify interactions only when the interaction *is* the behavior (event published, e-mail sent); otherwise assert on results.
- Context caching: keep the number of distinct Spring test configurations low (same `@MockitoBean` sets, same properties) so contexts are reused. Prefer one shared `TestcontainersConfiguration` imported by all integration tests; containers are reused across the test run (static/singleton) and, locally, across runs with `testcontainers.reuse.enable=true` in `~/.testcontainers.properties` + `.withReuse(true)`.
- Never use H2 to test PostgreSQL code. Never `@Transactional` on integration tests as a cleanup strategy when the transaction boundary is under test (7.13).
- Coverage: JaCoCo report in CI; target ~80% lines on services/domain, but **branch coverage of business rules** is what matters. Do not write tests to hit a number. Optional: PIT mutation testing on the domain package.
- Flaky tests are bugs: fix or delete, never `@Disabled` without an issue link.

### 10.3 Controller tests (complete slice example)

This example uses the supported classic `MockMvc` DSL to make the entire fixture explicit. `MockMvcTester` with AssertJ remains a good project choice; follow existing style. Import the real filter chains, their message handlers and required configuration; supply a test decoder and clock. These classes are public where a cross-package import requires it.

The request includes every required field. Invalid-input assertions search for the field rather than depending on validation-error order. A mocked `OrderService` does **not** prove service method security or ownership: §10.5 tests the real service. Use the `application-test.yaml` values below, which avoid production environment placeholders in tests:

```yaml
# path: src/test/resources/application-test.yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://issuer.example.invalid
          audiences: guide-verification
app:
  cors:
    allowed-origins:
      - http://localhost:5173
```

```java
// path: src/test/java/com/acme/billing/order/OrderControllerTest.java
package com.acme.billing.order;

import com.acme.billing.shared.error.GlobalExceptionHandler;
import com.acme.billing.shared.security.CorsProperties;
import com.acme.billing.shared.security.ProblemDetailAccessDeniedHandler;
import com.acme.billing.shared.security.ProblemDetailAuthenticationEntryPoint;
import com.acme.billing.shared.security.SecurityConfig;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Slice HTTP: valida binding e filtros. O serviço mockado não prova autorização de método. */
@WebMvcTest(OrderController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, GlobalExceptionHandler.class,
        ProblemDetailAuthenticationEntryPoint.class, ProblemDetailAccessDeniedHandler.class,
        OrderControllerTest.Support.class})
class OrderControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean OrderService orderService;
    @MockitoBean JwtDecoder jwtDecoder;

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CorsProperties.class)
    static class Support {
        @Bean Clock clock() { return Clock.systemUTC(); }
    }

    @Test
    @DisplayName("retorna 201 e Location para corpo válido, incluindo preço unitário")
    @WithMockUser(authorities = "SCOPE_orders:write")
    void shouldReturn201WhenCreatingValidOrder() throws Exception {
        UUID id = UUID.fromString("0192a1b2-0000-7000-8000-000000000010");
        given(orderService.create(any())).willReturn(OrderTestData.response(id));

        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","items":[{"sku":"ABC-1","quantity":2,"unitPrice":10.00}]}
                                """.formatted(OrderTestData.CUSTOMER_ID)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/orders/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    @DisplayName("quantidade inválida retorna erro de campo sem refletir o valor rejeitado")
    @WithMockUser(authorities = "SCOPE_orders:write")
    void shouldReturn400WhenQuantityIsZero() throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","items":[{"sku":"ABC-1","quantity":0,"unitPrice":10.00}]}
                                """.formatted(OrderTestData.CUSTOMER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[*].field", hasItem("items[0].quantity")))
                .andExpect(jsonPath("$.errors[*].rejectedValue").doesNotExist());
        verifyNoInteractions(orderService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[null]", "[]"})
    @DisplayName("lista ou elemento nulo é reportado pela validação, sem NPE no DTO")
    @WithMockUser(authorities = "SCOPE_orders:write")
    void shouldReportValidationForInvalidItems(String items) throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"" + OrderTestData.CUSTOMER_ID + "\",\"items\":" + items + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("requisição anônima recebe Problem Details e desafio Bearer")
    void shouldReturn401WhenAnonymous() throws Exception {
        mvc.perform(get("/api/orders/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
```

### 10.4 Testcontainers configuration (shared)

```java
// path: src/test/java/com/acme/billing/TestcontainersConfiguration.java
package com.acme.billing;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Infraestrutura de teste compartilhada. Um único container por JVM de testes (bean singleton),
 * conectado automaticamente ao DataSource via @ServiceConnection — sem @DynamicPropertySource.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        // Testcontainers 2.x: classe relocada para org.testcontainers.postgresql
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
    }
}
```

Usage: `@DataJpaTest @Import(TestcontainersConfiguration.class)` or `@SpringBootTest @Import(TestcontainersConfiguration.class)`. Since Boot 3.4 the default `@AutoConfigureTestDatabase(replace = NON_TEST)` keeps a DataSource that comes from a test configuration/`@ServiceConnection`, so no `replace = NONE` is required. For local development, `TestBillingApplication` (`SpringApplication.from(BillingApplication::main).with(TestcontainersConfiguration.class).run(args)`) starts the app against the container.

### 10.5 Full-context integration: authorization, migrations and persistence

The following test uses actual Spring Security chains, method security, `OrderService`, Flyway and PostgreSQL. `MockMvc` exercises the server stack in-process; it does not prove a real socket, TLS, proxy or browser works. Add a real HTTP client test when those boundaries are in scope. The test deliberately has no enclosing `@Transactional`, so request commits and database constraints are observable.

The decoder and customer feature are test-only substitutes. The decoder does not validate signatures, issuer, audience or token expiry. A production/IdP contract suite must separately reject invalid signatures, wrong issuer/audience, expired/not-yet-valid tokens and unexpected algorithms. Never ship the test decoder in `src/main` or enable a production bypass profile.

The PostgreSQL container is not skipped when Docker is missing: inability to run is a pending gate. Run `verify` so Failsafe actually includes `*IT`. For stable CI, replace the illustrative image tag with the project's approved immutable digest.

```java
// path: src/test/java/com/acme/billing/order/OrderApiIT.java
package com.acme.billing.order;

import com.acme.billing.customer.CustomerApi;
import com.acme.billing.order.dto.OrderResponse;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Filtros reais, serviço real, Flyway e PostgreSQL. Sem @Transactional no teste:
 * cada requisição deve efetivamente concluir sua transação.
 * O decoder falso isola assinatura/issuer/audience, que precisam de outro teste com o IdP.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Import({OrderApiIT.Support.class, OrderApiIT.OutsideController.class})
class OrderApiIT {
    private static final UUID OWNER = OrderTestData.CUSTOMER_ID;
    private static final UUID OTHER = UUID.fromString("0192a1b2-0000-7000-8000-000000000002");
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;

    @TestConfiguration(proxyBeanMethods = false)
    static class Support {
        @Bean CustomerApi customerApi() { return id -> Set.of(OWNER, OTHER).contains(id); }

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                if (!Set.of("owner", "other", "no-scope", "missing-customer", "ops").contains(token)) {
                    throw new BadJwtException("Invalid test token");
                }
                var builder = Jwt.withTokenValue(token).header("alg", "RS256")
                        .subject("fixture-" + token).issuedAt(Instant.now().minusSeconds(30))
                        .expiresAt(Instant.now().plusSeconds(300));
                if (!token.equals("missing-customer")) {
                    builder.claim("customer_id", (token.equals("other") ? OTHER : OWNER).toString());
                }
                String scope = switch (token) {
                    case "no-scope" -> "profile";
                    case "ops" -> "ops:read";
                    default -> "orders:read orders:write";
                };
                return builder.claim("scope", scope).build();
            };
        }
    }

    /** Existiria e responderia 200 sem a cadeia final: prova concreta da cobertura fora da API. */
    @RestController
    static class OutsideController {
        @GetMapping("/outside-probe")
        String outside() { return "must not be public"; }
    }

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("delete from order_items");
        jdbc.update("delete from orders");
    }

    @Test
    @DisplayName("persiste dois itens com posição não nula já no INSERT")
    void shouldPersistOrderedItemsWithMigrationConstraints() throws Exception {
        UUID id = createOrder("owner", OWNER);
        assertThat(jdbc.queryForList("select position from order_items where order_id = ? order by position",
                Integer.class, id)).containsExactly(0, 1);
        mvc.perform(get("/api/orders/{id}", id).header("Authorization", "Bearer owner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].sku").value("ABC-1"))
                .andExpect(jsonPath("$.items[1].sku").value("DEF-2"));
    }

    @Test
    @DisplayName("outro cliente não lê nem cancela pedido, mesmo conhecendo o UUID")
    void shouldHideForeignOrderFromReadAndCancel() throws Exception {
        UUID id = createOrder("owner", OWNER);
        mvc.perform(get("/api/orders/{id}", id).header("Authorization", "Bearer other"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/orders/{id}/cancellation", id).header("Authorization", "Bearer other")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"tentativa\"}"))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select status from orders where id = ?", String.class, id))
                .isEqualTo("PENDING");
    }

    @Test
    @DisplayName("listagem sem filtro continua restrita ao cliente autenticado")
    void shouldRestrictSearchEvenWithoutCustomerFilter() throws Exception {
        UUID ownId = createOrder("owner", OWNER);
        createOrder("other", OTHER);
        mvc.perform(get("/api/orders").header("Authorization", "Bearer owner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(ownId.toString()));
        mvc.perform(get("/api/orders").param("customerId", OTHER.toString())
                        .header("Authorization", "Bearer owner"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("não aceita customerId de terceiro no corpo da criação")
    void shouldRejectCreationForForeignCustomer() throws Exception {
        mvc.perform(post("/api/orders").header("Authorization", "Bearer owner")
                        .contentType(MediaType.APPLICATION_JSON).content(orderBody(OTHER)))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select count(*) from orders", Long.class)).isZero();
    }

    @Test
    @DisplayName("falta de escopo e falta de identidade do cliente retornam 403, sem virar 500")
    void shouldPreserve403FromRealMethodSecurityAndIdentityAdapter() throws Exception {
        mvc.perform(get("/api/orders").header("Authorization", "Bearer no-scope"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/api/orders").header("Authorization", "Bearer missing-customer"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("cobre uma rota real fora dos matchers de API e Actuator")
    void shouldDenyRouteOutsideScopedChains() throws Exception {
        mvc.perform(get("/outside-probe")).andExpect(status().isUnauthorized());
        mvc.perform(get("/outside-probe").header("Authorization", "Bearer owner"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("protege Actuator com o mesmo mapeamento de authorities do token")
    void shouldRequireOperationalScopeForActuatorInfo() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/info").header("Authorization", "Bearer owner"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/actuator/info").header("Authorization", "Bearer ops"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("resposta de cancelamento reflete versão gravada no commit")
    void shouldReturnCommittedCancellationVersion() throws Exception {
        UUID id = createOrder("owner", OWNER);
        mvc.perform(post("/api/orders/{id}/cancellation", id).header("Authorization", "Bearer owner")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"solicitado\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.version").value(1));
        assertThat(jdbc.queryForObject("select version from orders where id = ?", Long.class, id)).isEqualTo(1L);
    }

    private UUID createOrder(String token, UUID customer) throws Exception {
        var result = mvc.perform(post("/api/orders").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(orderBody(customer)))
                .andExpect(status().isCreated()).andReturn();
        return json.readValue(result.getResponse().getContentAsString(), OrderResponse.class).id();
    }

    private static String orderBody(UUID customer) {
        return """
                {"customerId":"%s","items":[
                  {"sku":"abc-1","quantity":2,"unitPrice":10.00},
                  {"sku":"def-2","quantity":1,"unitPrice":7.00}
                ]}
                """.formatted(customer);
    }
}
```

### 10.6 Architecture tests (ArchUnit)

Use the JUnit-compatible ArchUnit artifact (Part 1.3). These rules check actual bytecode dependencies and constructor-injection policy; expand them only for architecture decisions the project has adopted.

```java
// path: src/test/java/com/acme/billing/ArchitectureTest.java
package com.acme.billing;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

class ArchitectureTest {
    @Test
    @DisplayName("controllers não acessam repositórios diretamente")
    void shouldKeepPersistenceBehindServices() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.acme.billing");
        noClasses().that().haveSimpleNameEndingWith("Controller").should()
                .dependOnClassesThat().haveSimpleNameEndingWith("Repository").check(classes);
    }

    @Test
    @DisplayName("features não formam ciclos de dependência")
    void shouldKeepFeaturesAcyclic() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.acme.billing");
        slices().matching("com.acme.billing.(*)..").should().beFreeOfCycles().check(classes);
    }

    @Test
    @DisplayName("dependências Spring não são injetadas em campos")
    void shouldUseConstructorInjection() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.acme.billing");
        noFields().should().beAnnotatedWith(Autowired.class).check(classes);
    }
}
```

### 10.7 Unit test example (complete service example)

The repository stub matches the real owner-qualified query. Unit tests prove orchestration and domain outcomes; they do not activate proxy-based `@Transactional` or `@PreAuthorize`. §10.5 covers those boundaries.

```java
// path: src/test/java/com/acme/billing/order/OrderServiceTest.java
package com.acme.billing.order;

import com.acme.billing.customer.CustomerApi;
import com.acme.billing.order.dto.CancelOrderRequest;
import com.acme.billing.order.dto.SearchOrdersRequest;
import com.acme.billing.order.event.OrderCancelledEvent;
import com.acme.billing.shared.security.CurrentCustomer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    private static final UUID OTHER_CUSTOMER = UUID.fromString("0192a1b2-0000-7000-8000-000000000002");
    @Mock OrderRepository orderRepository;
    @Mock CustomerApi customerApi;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock CurrentCustomer currentCustomer;
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-10T12:00:00Z"), ZoneOffset.UTC);
    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(orderRepository, customerApi, eventPublisher, clock, currentCustomer);
    }

    @Test
    @DisplayName("rejeita cancelamento de pedido entregue sem publicar evento")
    void shouldRejectCancellationWhenOrderDelivered() {
        var order = OrderTestData.delivered();
        given(currentCustomer.id()).willReturn(OrderTestData.CUSTOMER_ID);
        given(orderRepository.findWithItemsByIdAndCustomerId(order.getId(), OrderTestData.CUSTOMER_ID))
                .willReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancel(order.getId(), new CancelOrderRequest("motivo")))
                .isInstanceOf(InvalidOrderStateException.class).hasMessageContaining("DELIVERED");

        then(eventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("não cancela nem revela pedido de outro cliente")
    void shouldReturnNotFoundWhenOrderDoesNotBelongToCaller() {
        UUID orderId = UUID.fromString("0192a1b2-0000-7000-8000-000000000010");
        given(currentCustomer.id()).willReturn(OTHER_CUSTOMER);
        given(orderRepository.findWithItemsByIdAndCustomerId(orderId, OTHER_CUSTOMER))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(orderId, new CancelOrderRequest("motivo")))
                .isInstanceOf(OrderNotFoundException.class);

        then(eventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("rejeita criação para cliente diferente do token antes de gravar")
    void shouldRejectCreationForAnotherCustomer() {
        given(currentCustomer.id()).willReturn(OTHER_CUSTOMER);

        assertThatThrownBy(() -> service.create(OrderTestData.createRequest()))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(customerApi, orderRepository, eventPublisher);
    }

    @Test
    @DisplayName("rejeita filtro de cliente conflitante antes de consultar")
    void shouldRejectForeignCustomerFilter() {
        given(currentCustomer.id()).willReturn(OrderTestData.CUSTOMER_ID);
        var filter = new SearchOrdersRequest(OTHER_CUSTOMER, null, null, null);

        assertThatThrownBy(() -> service.search(filter, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(orderRepository);
    }

    @Test
    @DisplayName("cancela pedido próprio com relógio controlado e publica o evento")
    void shouldCancelOwnedPendingOrder() {
        var order = OrderTestData.pending();
        given(currentCustomer.id()).willReturn(OrderTestData.CUSTOMER_ID);
        given(orderRepository.findWithItemsByIdAndCustomerId(order.getId(), OrderTestData.CUSTOMER_ID))
                .willReturn(Optional.of(order));

        var response = service.cancel(order.getId(), new CancelOrderRequest("solicitação do cliente"));

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(response.cancelledAt()).isEqualTo(clock.instant());
        then(orderRepository).should().flush();
        then(eventPublisher).should().publishEvent(new OrderCancelledEvent(order.getId(), "solicitação do cliente"));
    }
}
```

---

## Part 11 — Configuration, build and operations

### 11.1 `application.yaml` (base + profiles)

- One `application.yaml` with sane defaults + `application-local.yaml` (developer machine), `application-test.yaml` (only if tests need overrides), and environment-specific values injected via env vars in `dev`/`staging`/`prod` (12-factor). Avoid `application-prod.yaml` full of values; production config comes from the platform.
- Group project properties under a single prefix (`app.*`) bound to records (5.5). Document every property in pt-BR JavaDoc.
- Use `spring.config.import=optional:configtree:/run/secrets/` for mounted secrets, `optional:file:./.env[.properties]` locally.
- Profiles: `spring.profiles.active` set by the environment, never hardcoded; `@Profile` on beans only for infrastructure substitutes (e.g., a fake e-mail sender in `local`).

```yaml
spring:
  application:
    name: billing-service
  threads:
    virtual:
      enabled: true
  main:
    banner-mode: "off"
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${JWT_ISSUER_URI}
          audiences: ${JWT_AUDIENCE}
  messages:
    fallback-to-system-locale: false
  jackson:
    default-property-inclusion: non_null
    time-zone: UTC
  mvc:
    problemdetails:
      enabled: true
    apiversion:
      use:
        header: X-API-Version
      supported: "1.0"
      default: "1.0"
  data:
    web:
      pageable:
        default-page-size: 20
        max-page-size: 100
        serialization-mode: via_dto
  jpa: { open-in-view: false, hibernate: { ddl-auto: validate } }
  lifecycle:
    timeout-per-shutdown-phase: 30s
server:
  shutdown: graceful
  compression:
    enabled: true
    additional-mime-types: application/problem+json
  http2:
    enabled: true
  error:
    include-message: never
    include-stacktrace: never
    include-binding-errors: never
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  endpoint:
    health:
      show-details: when_authorized
      probes:
        enabled: true
  tracing:
    sampling:
      probability: 0.1
  otlp:
    tracing:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318/v1/traces}   # confirmar a chave no configuration changelog do Boot 4.x antes de usar
logging:
  structured:
    format:
      console: ecs      # sobrescrever para vazio no perfil local (log legível)
  level:
    root: INFO
    com.acme: INFO
    org.hibernate.SQL: WARN
app:
  cors:
    allowed-origins: ${CORS_ALLOWED_ORIGIN}
```

The JWT issuer/audience, explicit CORS origin and database configuration (Part 7.8) are required inputs to the full feature. The selected IdP supplies `customer_id` and `scope`; do not accept a customer id from an unsigned header. Optional tracing properties should be verified for the selected exporter rather than copied unchanged.

### 11.2 Build — Maven (reference `pom.xml` fragments)

- Parent `spring-boot-starter-parent` 4.1.x (or import `spring-boot-dependencies` BOM). `<java.version>25</java.version>` → `maven.compiler.release=25`. Use the Maven wrapper committed to the repo.
- Plugins: `spring-boot-maven-plugin` (repackage, `build-info`, `build-image`), `maven-compiler-plugin` with `annotationProcessorPaths` (Part 4.4) and `-parameters` (Boot parent sets it), `maven-surefire-plugin` (unit) + `maven-failsafe-plugin` (`*IT`), `jacoco-maven-plugin`, `spotless-maven-plugin` (format check in CI), `maven-enforcer-plugin` (`requireMavenVersion`, `requireJavaVersion`, `banDuplicatePomDependencyVersions`, `dependencyConvergence`), `git-commit-id-maven-plugin`, `cyclonedx-maven-plugin` (SBOM — Boot's build plugin can also produce it), OWASP `dependency-check-maven` or Renovate/Dependabot for updates. Optional: `errorprone` + `nullaway` for JSpecify enforcement.
- Never pin versions that the Boot BOM manages; pin only non-managed ones in `<properties>`. Run `./mvnw versions:display-dependency-updates` periodically (or automate with Renovate).
- Reproducible builds: `project.build.outputTimestamp` set; no `SNAPSHOT` dependencies in `main`.

```xml
<properties>
  <java.version>25</java.version>
  <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
  <mapstruct.version>1.6.3</mapstruct.version>
  <lombok-mapstruct-binding.version>0.2.0</lombok-mapstruct-binding.version>
</properties>

<build>
  <plugins>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-compiler-plugin</artifactId>
      <configuration>
        <release>${java.version}</release>
        <parameters>true</parameters>
        <compilerArgs><arg>-Xlint:all,-processing,-serial</arg></compilerArgs>
        <annotationProcessorPaths>
          <path><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId><version>${lombok.version}</version></path>
          <path><groupId>org.projectlombok</groupId><artifactId>lombok-mapstruct-binding</artifactId><version>${lombok-mapstruct-binding.version}</version></path>
          <path><groupId>org.mapstruct</groupId><artifactId>mapstruct-processor</artifactId><version>${mapstruct.version}</version></path>
          <path><groupId>org.hibernate.orm</groupId><artifactId>hibernate-processor</artifactId><version>${hibernate.version}</version></path>
          <path><groupId>org.springframework.boot</groupId><artifactId>spring-boot-configuration-processor</artifactId><version>${project.parent.version}</version></path>
        </annotationProcessorPaths>
      </configuration>
    </plugin>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-javadoc-plugin</artifactId>
      <configuration>
        <encoding>UTF-8</encoding>
        <docencoding>UTF-8</docencoding>
        <charset>UTF-8</charset>
        <doclint>all,-missing</doclint>
      </configuration>
    </plugin>
    <plugin>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-maven-plugin</artifactId>
      <executions>
        <execution><goals><goal>build-info</goal></goals></execution>
      </executions>
      <configuration>
        <excludes><exclude><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId></exclude></excludes>
      </configuration>
    </plugin>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-failsafe-plugin</artifactId>
      <executions><execution><goals><goal>integration-test</goal><goal>verify</goal></goals></execution></executions>
    </plugin>
    <plugin>
      <groupId>com.diffplug.spotless</groupId>
      <artifactId>spotless-maven-plugin</artifactId>
      <configuration>
        <java>
          <palantirJavaFormat/>
          <removeUnusedImports/>
          <importOrder/>
        </java>
      </configuration>
      <executions><execution><goals><goal>check</goal></goals><phase>verify</phase></execution></executions>
    </plugin>
    <plugin>
      <groupId>org.jacoco</groupId>
      <artifactId>jacoco-maven-plugin</artifactId>
      <executions>
        <execution><goals><goal>prepare-agent</goal></goals></execution>
        <execution><id>report</id><phase>verify</phase><goals><goal>report</goal></goals></execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

(Versions of Spotless, JaCoCo, enforcer and MapStruct are not managed by Boot — set them explicitly in `<pluginManagement>`/`<properties>` to the current releases; the agent must not invent version numbers.)

- Gradle (Kotlin DSL) equivalent: `java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }`, `id("org.springframework.boot")`, `id("io.spring.dependency-management")` or platform BOM, `spotless`, `jacoco`, `compileOnly`/`annotationProcessor` for Lombok/MapStruct, `tasks.test { useJUnitPlatform() }`, separate `integrationTest` source set or tags.

### 11.3 Local development

- `spring-boot-docker-compose` + a `compose.yaml` at the repo root (PostgreSQL, optionally Redis/Kafka/Keycloak/OTel collector): Boot starts the containers on `./mvnw spring-boot:run` and wires connection details automatically. Alternatively `TestBillingApplication` with Testcontainers (10.4).
- `spring-boot-devtools` for restart; live reload is off by default in Boot 4 (`spring.devtools.livereload.enabled=true` if wanted).
- `.editorconfig`, `lombok.config`, `.gitattributes` (LF), `README.md` with "how to run" in pt-BR, `CONTRIBUTING.md` pointing to this guide.

### 11.4 Container image and deployment

- Build with Buildpacks: `./mvnw spring-boot:build-image -Dspring-boot.build-image.imageName=acme/billing:1.2.0` (layered, non-root, JVM memory calculator, CDS/AOT support) **or** a multi-stage `Dockerfile` using `java -Djarmode=tools -jar app.jar extract --layers --destination extracted` and a distroless/`eclipse-temurin:25-jre` runtime image. Never run as root; read-only filesystem; `tmpfs` for `/tmp`.
- JVM flags for containers (Java 25): `-XX:+UseContainerSupport` (default), `-XX:MaxRAMPercentage=75.0`, `-XX:+UseCompactObjectHeaders`, `-XX:+ExitOnOutOfMemoryError`, `-Duser.timezone=UTC`, `-Dfile.encoding=UTF-8` (default), GC: G1 (default) for typical services, `-XX:+UseZGC` (generational is now the only ZGC) for large heaps/latency-sensitive, `-XX:+UseShenandoahGC` generational as an alternative. Do not set `-Xmx` and `MaxRAMPercentage` together.
- **Startup time**: measure a baseline before enabling JDK AOT cache or Spring AOT. Train with the same JDK build, classpath and relevant JVM options as runtime, require successful training, verify the cache exists, and test application startup with it. Training may instantiate application beans and contact services; use an explicit build profile and safe fixtures. Never mask training failure with `|| true` and then require the missing cache at runtime. The reference Dockerfile uses an ordinary JRE startup; AOT/native is an optional, separately verified optimization.
- Kubernetes: readiness/liveness from actuator (`/actuator/health/readiness`, `/liveness`), `terminationGracePeriodSeconds` > `spring.lifecycle.timeout-per-shutdown-phase`, resource requests/limits aligned with `MaxRAMPercentage`, `preStop` sleep for load-balancer drain, HPA on CPU/RPS, PodDisruptionBudget.
- Config via env vars / mounted secrets; never bake secrets into images.
- Migrations run at app startup (Flyway) for single-instance deploys; for multi-replica rollouts run migrations as an init job/step before rolling pods, and keep migrations backward compatible (7.7).

### 11.5 CI pipeline (minimum)

1. `./mvnw -B verify` (format check, compile with `-Xlint`, unit + integration tests with Testcontainers, JaCoCo).
2. Dependency vulnerability scan (OWASP dependency-check / Trivy on the image) and SBOM publication.
3. Build image, tag with git SHA + semver, push.
4. Optional: OpenAPI diff against `main` to flag breaking changes; ArchUnit is part of tests.

### 11.6 Runtime tuning checklist

- Virtual threads on; HikariCP sized for the DB (start 10, measure); statement/connection/socket timeouts everywhere; `server.tomcat.threads.max` irrelevant with virtual threads but keep `server.tomcat.max-connections` sane.
- Graceful shutdown on; health probes on; structured logs on; tracing sampled.
- Enable `-XX:+UseCompactObjectHeaders`; consider `-XX:+AlwaysPreTouch` off in containers; JFR on demand (`-XX:StartFlightRecording` with a duration) for profiling incidents — JDK 25 adds CPU-time profiling and method timing/tracing events.

---

## Part 12 — Concurrency, scheduling and resilience in Spring

### 12.1 `@Async` and executors

- `@EnableAsync` on a configuration class; with `spring.threads.virtual.enabled=true` Boot's `applicationTaskExecutor` is a virtual-thread executor — use it (default) instead of defining `ThreadPoolTaskExecutor`s.
- `@Async` methods return `void` or `CompletableFuture<T>`; exceptions in `void` methods go to `AsyncUncaughtExceptionHandler` — always configure one that logs with context.
- Same proxy caveats as `@Transactional`: no self-invocation, public methods only.
- Propagate MDC/security/trace context with a `TaskDecorator` bean (Boot composes multiple `TaskDecorator`s in 4.x) or Micrometer Context Propagation.
- Never fire-and-forget business-critical work in memory; use the outbox + a scheduler/queue.

### 12.2 `@Scheduled`

- `@EnableScheduling`; cron with an explicit zone (`@Scheduled(cron = "0 0 3 * * *", zone = "America/Fortaleza")`); `fixedDelay` over `fixedRate` for jobs that must not overlap.
- In multi-replica deployments a job runs on every instance: use **ShedLock** (`@SchedulerLock(name = "...", lockAtMostFor = "10m")`) or Quartz clustered, or move the job to a single worker deployment.
- Wrap the body in a try/catch that logs (uncaught exceptions are logged by Spring but swallowed); make jobs idempotent and resumable; add metrics (`@Timed`) and an observation.
- Scheduled tasks are observed (Boot `ScheduledTasksObservationAutoConfiguration`).

### 12.3 Resilience (Spring Framework 7 core)

- `@EnableResilientMethods` on a config class enables `@Retryable` and `@ConcurrencyLimit`.
- `@Retryable(includes = TransientException.class, maxRetries = 3, delay = 200, multiplier = 2, jitter = 100, maxDelay = 2000)` (`org.springframework.resilience.annotation.Retryable`; `maxRetries` counts retries *after* the initial attempt; `timeout` caps total elapsed time) on adapter methods calling external systems — **only for idempotent operations**; never retry non-idempotent writes without an idempotency key; never retry on `4xx` except `408/429` (with `Retry-After`).
- Programmatic: `org.springframework.core.retry.RetryTemplate` with a `RetryPolicy` (exponential backoff + jitter, max attempts, retryable predicate).
- `@ConcurrencyLimit(10)` to bound concurrent executions of an expensive method (protects downstream and memory).
- Circuit breaker, bulkhead, rate limiter: **Resilience4j** (`resilience4j-spring-boot3` compatible artifact for Boot 4 — verify the current artifact; the Spring Cloud Circuit Breaker abstraction is another option). Configure per external dependency; fallbacks return degraded but valid responses or fail fast with `503` + `Retry-After`.
- Timeouts are the first line of resilience: connect/read timeouts on `RestClient`/HTTP services (`spring.http.serviceclient.<group>.connect-timeout`), JDBC (`connection-timeout`, statement timeouts), messaging.

### 12.4 Messaging and events (when part of the task)

- In-process: `ApplicationEventPublisher` + `@TransactionalEventListener(phase = AFTER_COMMIT)` (+ `@Async` if the handler is slow). Events are immutable records named in the past tense.
- Cross-service: **transactional outbox** (Spring Modulith's event publication registry with `@Externalized` to Kafka/RabbitMQ/SQS, or a hand-written outbox table + relay). Consumers are **idempotent** (dedupe by event id). At-least-once delivery is the assumption everywhere.
- Kafka/RabbitMQ listeners run on virtual threads when enabled; configure error handling (dead-letter topic/queue, retry with backoff via Spring Kafka/AMQP's Spring Framework 7 retry integration), and observation.
- Message schemas are versioned (JSON with explicit `version`, or Avro/Protobuf with a registry); never publish entities.

---

## Part 13 — Design patterns catalog (Java 21+ / Spring idioms)

Rule of use: a pattern is justified only when it removes a concrete problem (duplication, rigidity, hidden coupling, untestable code) that exists **now**. Name the pattern in one sentence when you apply it. Prefer the language/framework feature that already implements the pattern over a hand-written class hierarchy.

### 13.1 Creational

| Pattern | When | Modern idiom | Do not |
|---|---|---|---|
| **Static factory method** | Always preferred over public constructors for value objects/aggregates: named creation (`Money.of`, `Order.create`), validation, caching, returning subtypes. | `public static Order create(Customer c, List<Item> items)`; records with compact constructors. | Expose constructors that allow invalid state. |
| **Builder** | Objects with many optional parameters; test data; immutable classes that cannot be records. | Lombok `@Builder` (Part 4), record + `@Builder`, or hand-written builder for aggregates; `toBuilder = true` for copies. | Builders for 3-field records; builders that skip validation (`build()` must validate). |
| **Factory / Abstract Factory** | Choosing an implementation at runtime by a key (payment provider, document type). | Spring DI: inject `Map<String, PaymentProvider>` (bean name → bean) or `List<PaymentProvider>` and select by `supports(type)`; `ObjectProvider`; `sealed` + `switch` for closed sets. | A `PaymentProviderFactory` class with a `switch` on strings when Spring can inject the map. |
| **Prototype** | Rare in Java. | Records `with...` methods / Lombok `@With`; `toBuilder()`. | `Object.clone()`. |
| **Singleton** | Every Spring bean is a singleton. | `@Component`/`@Bean`. | Hand-written singletons, static instances, enums-as-singletons for services. |
| **Object Pool** | Connections, expensive resources. | HikariCP, HTTP client pools (already pooled). | Pooling virtual threads or cheap objects. |
| **Dependency Injection** | Always. | Constructor injection (Part 5.4). | Field injection, service locators (`ApplicationContext.getBean` in business code). |

### 13.2 Structural

| Pattern | When | Modern idiom | Do not |
|---|---|---|---|
| **Adapter (Ports & Adapters)** | Isolating external systems (HTTP APIs, SEFAZ, e-mail, storage, legacy DB) from the domain. | Domain-side interface (`PaymentGateway`) + infrastructure implementation (`RestPaymentGateway` using `RestClient`/HTTP interface); tests use a fake. | Leaking client DTOs/exceptions into services. |
| **Facade** | Simplifying a subsystem with a single entry point. | The application service *is* the facade for a use case; `XxxFacade` only when composing several services for a coarse API/BFF. | Facades that just forward one call. |
| **Decorator** | Adding behavior (caching, retry, metrics, logging) around a component without touching it. | Spring AOP/annotations (`@Cacheable`, `@Retryable`, `@Observed`, `@Transactional`) — they *are* decorators; explicit decorator class + `@Primary` when behavior must be composable/testable; `RestClient` interceptors, `HandlerInterceptor`, `Filter`. | Deep manual decorator chains; decorators with state. |
| **Proxy** | Lazy loading, access control, remote proxies. | Hibernate lazy proxies, Spring AOP proxies, HTTP interface clients (proxies generated by Spring). | Hand-written proxies. |
| **Composite** | Tree structures with uniform operations (rules, menu, org chart, specifications). | `sealed interface Node permits Leaf, Group` + records + pattern matching; `Specification.and/or`. | Composite for flat lists. |
| **Bridge** | Decoupling abstraction from implementation across two dimensions (report format × delivery channel). | Two interfaces injected into each other via Spring; often plain composition. | Introducing a bridge for one dimension. |
| **Flyweight** | Many identical immutable values. | `Integer.valueOf` cache, enum constants, interning, `List.of` singletons; rarely needed explicitly. | Premature. |
| **Anti-Corruption Layer** | Integrating with a legacy/external model. | Translator/mapper at the boundary producing domain records; never expose the external model. | Letting external field names spread across the domain. |

### 13.3 Behavioral

| Pattern | When | Modern idiom | Do not |
|---|---|---|---|
| **Strategy** | Interchangeable algorithms chosen at runtime (pricing rules, tax by state, discount policies, notification channels). | Interface + Spring beans (`List<DiscountPolicy>` iterated with `supports`); `Map<Key, Strategy>`; for closed sets, `sealed` + `switch`; for one-liners, `Function`/`BiFunction`/lambdas; enum with behavior for fixed strategies. | An interface with one implementation; `if/else` ladders on a type string in five places. |
| **Template Method** | Fixed algorithm skeleton with variable steps. | Prefer composition: a method that takes `Function`/`Supplier` steps, or a Strategy; if inheritance is used, keep the hierarchy shallow with `abstract` steps. | Deep abstract base-class hierarchies (`AbstractBaseService`). |
| **Observer / Publish–Subscribe** | Reacting to domain events without coupling features. | `ApplicationEventPublisher` + `@EventListener` / `@TransactionalEventListener`; Spring Modulith events; messaging for cross-service. | Callbacks lists managed by hand; synchronous listeners doing heavy work in the request. |
| **Command** | Encapsulating a request as an object: undo/redo, queues, audit trails, CQRS commands. | Records as commands (`CreateOrderCommand`), a handler per command (`OrderCommandHandler`), a dispatcher only if there are many; `sealed interface Command permits ...`. | A `Command` interface with `execute()` for every service call. |
| **Chain of Responsibility** | Sequential handlers that may stop the chain (validation pipelines, request filters, fraud checks). | Servlet `Filter`s/`HandlerInterceptor`s; an ordered `List<Check>` with `@Order`; `Stream.allMatch`/`takeWhile`. | Linked handler objects wiring each other manually. |
| **State** | Objects whose behavior depends on state with many transitions (order lifecycle, document workflow). | Enum + transition methods with precondition checks on the aggregate; `sealed` state records when states carry data; a transition table (`Map<State, Set<State>>`) for validation. Spring Statemachine only for large workflows. | Boolean flags (`isPaid`, `isShipped`, `isCancelled`) instead of a state. |
| **Visitor** | Operations over a closed hierarchy without modifying it. | **Pattern matching `switch` over a `sealed` hierarchy** replaces Visitor entirely (exhaustive, no double dispatch boilerplate). | Classic Visitor interfaces in new code. |
| **Iterator** | Traversal. | `Iterable`/`Stream`/`Gatherers`; `Spliterator` for custom sources. | Custom iterator classes. |
| **Mediator** | Reducing many-to-many dependencies between components. | Application service coordinating aggregates; event bus; Spring's context. | A God mediator. |
| **Memento** | Snapshots for undo/audit. | Immutable records as snapshots; event sourcing when history is the model. | Serializable memento hacks. |
| **Interpreter / Specification** | Composable business rules and queries. | Spring Data `Specification` (JPA); domain `Predicate<T>` combinators (`and`, `or`, `negate`); rules as records + evaluator. | String rule engines for three rules. |
| **Null Object** | Avoiding null checks for optional collaborators. | Provide a no-op implementation as a `@Bean` under a profile/condition (`NoOpNotifier`); `Optional` return types; empty collections. | Returning `null` from services. |
| **Result / Either** | Expected failures that callers must handle explicitly (parsing, validation with multiple errors), without exceptions. | `sealed interface Result<T> permits Ok, Err` + records + `switch`. Use exceptions for exceptional flows and Problem Details at the boundary; use `Result` inside the domain when failures are part of the normal flow. | Wrapping every method in `Result`. |

### 13.4 Architectural / enterprise patterns

| Pattern | When | Modern idiom |
|---|---|---|
| **Repository** | Always, per aggregate root. | Spring Data interface; custom fragments for complex queries. |
| **Unit of Work** | Always (implicit). | JPA persistence context + `@Transactional` at the service. |
| **DTO** | Always at boundaries. | Records (Part 6.4). |
| **Value Object** | Any concept with equality by value. | Records + `@Embeddable`. |
| **Aggregate** | Consistency boundary. | Root entity with behavior; children only through the root; one repository per root. |
| **Domain Event / Outbox** | Reliable side effects and integration. | `@TransactionalEventListener` in-process; outbox (Spring Modulith event publication registry / custom table + relay) cross-service. |
| **CQRS (light)** | Reads with different shapes/perf than writes. | Query services returning projections; commands through aggregates. Separate read DB only when justified. |
| **Saga / Process Manager** | Multi-step business processes across services. | Orchestrated saga with a state entity + outbox + idempotent steps + compensations. Avoid for in-process flows. |
| **Idempotent Receiver** | Any message/webhook consumer; POST with retries. | Dedupe table keyed by event id / `Idempotency-Key` (6.10). |
| **Circuit Breaker / Retry / Bulkhead / Timeout** | External dependencies. | Part 12.3. |
| **Cache-Aside** | Read-heavy stable data. | `@Cacheable` + TTL + `@CacheEvict` (5.8). |
| **Feature Toggle** | Gradual rollout. | Properties record + `@ConditionalOnProperty` or a flag service; remove toggles after rollout. |
| **Anti-Corruption Layer** | Legacy integration. | 13.2. |
| **Transactional Script vs Domain Model** | Small CRUD features may stay as transaction scripts (service + repository, thin entity); complex rules move to a domain model. Choose per feature; do not force one style on everything. |

### 13.5 Anti-patterns to reject on sight

- Singleton/Service Locator/static registries; `ApplicationContextProvider` helpers.
- God classes (`OrderManager` with 40 methods); `Utils`/`Helper` dumps; `BaseService<T, ID>` generic CRUD services that every service extends.
- Anemic model *plus* fat services duplicating rules across services; or the opposite, entities calling repositories/HTTP.
- Interface-for-everything (`FooService` + `FooServiceImpl` with one implementation).
- Exceptions as control flow; checked exceptions leaking through services; swallowed exceptions.
- DTO ↔ entity mapping by reflection; entities in the API; `Map<String,Object>` payloads.
- Boolean parameters that switch behavior (`save(order, true)`); long parameter lists (> 4 → introduce a record).
- Premature abstraction "for future providers"; speculative generality; configuration for everything.
- `@Transactional` on controllers or on `private` methods; `REQUIRES_NEW` sprinkled to "fix" rollback problems.
- Business logic in `@PrePersist`/`@PostLoad` callbacks or in database triggers without a documented reason.

---

## Part 14 — Anti-patterns table (quick reference)

| Anti-pattern | Why it is wrong | Correct approach |
|---|---|---|
| `spring-boot-starter-web`, `@MockBean`, `ObjectMapper` bean, `org.springframework.lang.Nullable` | Boot 3 idioms; deprecated/removed in Boot 4 | Part 1.2 table |
| Returning `Page<T>` directly | JSON structure unstable | `PagedModel<T>` (`via_dto`) or `PageResponse<T>` |
| `join fetch` collection + `Pageable` | In-memory pagination, full table load | Paginate ids then fetch; `@BatchSize`; projections |
| `spring.jpa.open-in-view=true` (default) | Hides lazy-loading bugs; holds connections | `false`; load inside the transaction |
| `ddl-auto=update` | Unreviewed schema changes; drift; data loss | Flyway + `validate` |
| `IDENTITY` ids | No JDBC batching | `SEQUENCE` (allocation 50) or UUID v7 |
| `@Data` / `@EqualsAndHashCode` on entities | Broken equality with lazy proxies/generated ids; huge `toString` | `@Getter` + hand-written `equals/hashCode` (7.1) |
| Lombok `@Value`/`@Data` DTOs | Records exist | `record` |
| Field injection `@Autowired` | Untestable, hidden dependencies, circularity | Constructor injection |
| `@Transactional` on controller / repository / private method | Wrong boundary or no effect (proxy) | Service public methods |
| Catching `Exception` in services | Hides bugs, mis-maps errors | Specific exceptions; advice at the boundary |
| Custom error JSON (`{timestamp,status,error,message}`) | Reinvents RFC 9457 | `ProblemDetail` |
| `RestTemplate` / new `WebClient` in MVC app | Deprecated / wrong stack | `RestClient`, HTTP interfaces |
| `ThreadLocal` for request context in new code | Leaks, no VT inheritance | `ScopedValue` (own code); MDC/security via framework |
| Fixed thread pools for I/O | Wastes virtual threads | `spring.threads.virtual.enabled=true` |
| `Thread.sleep` in tests | Flaky, slow | Awaitility |
| H2 for tests of a PostgreSQL app | Dialect differences hide bugs | Testcontainers PostgreSQL |
| `List<Entity> findAll()` on unbounded tables | Memory/latency blow-up | Pagination, projections |
| Verbs in URLs (`/createOrder`), resource repeated in sub-paths | Not REST | Nouns + methods; `/orders/{id}/cancellation` |
| Tunnelling PUT/PATCH/DELETE through POST by default | Breaks HTTP semantics, caching, idempotency | Correct methods; document infra exceptions explicitly |
| Sensitive identifiers anywhere in URLs | Leak in logs/history, including query strings | Body-based search when needed, with logging redaction |
| Sensitive data in logs/`toString` | LGPD/PCI violations | Mask; never log tokens/passwords |
| `permitAll()` fallbacks, `csrf().disable()` with sessions | Security holes | Deny by default; CSRF on for cookie sessions |
| Hand-rolled JWT/crypto | Subtle vulnerabilities | Spring Security resource server; `PasswordEncoder` |
| `String` concatenation in JPQL/SQL | Injection | Bound parameters |
| Business rules in controllers / `@PrePersist` | Untestable, duplicated | Domain + service |
| `Date`/`Calendar`/`Timestamp` | Mutable, time-zone bugs | `java.time` + injected `Clock` |
| `double` for money | Rounding errors | `BigDecimal`/`Money` |
| `@Scheduled` without lock in multi-replica | Duplicate execution | ShedLock / single worker |
| Retrying non-idempotent calls | Duplicate side effects | Idempotency keys; retry only safe ops |
| Comments narrating code line by line | Noise, rots quickly | Comments explain *why*; JavaDoc on public API |
| Identifiers in Portuguese (except domain terms) / mixed languages | Inconsistent with ecosystem and tooling | English identifiers, pt-BR comments (Part 2) |

---

## Part 15 — Reference templates (copy the style, not just the code)

All templates share one example feature: **orders** in `com.acme.billing`. They show the mandatory style: English identifiers, pt-BR comments/JavaDoc, records for DTOs, JSpecify, Problem Details, Spring Boot 4 packages.

### 15.1 Application, package-info, shared configuration

```java
// path: src/main/java/com/acme/billing/BillingApplication.java
package com.acme.billing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Ponto de entrada do serviço de faturamento.
 *
 * <p>{@code @ConfigurationPropertiesScan} registra todos os records anotados com
 * {@code @ConfigurationProperties} sem precisar listá-los um a um.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class BillingApplication {

    public static void main(String[] args) {
        SpringApplication.run(BillingApplication.class, args);
    }
}
```

```java
// path: src/main/java/com/acme/billing/order/package-info.java
/**
 * Feature de pedidos: agregado {@code Order}, seus itens e a API REST correspondente.
 *
 * <p>Todo o pacote é {@code @NullMarked}: referências são não-nulas por padrão e as exceções
 * são marcadas explicitamente com {@code @Nullable}.</p>
 */
@NullMarked
package com.acme.billing.order;

import org.jspecify.annotations.NullMarked;
```

```java
// path: src/main/java/com/acme/billing/shared/config/ClockConfig.java
package com.acme.billing.shared.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Expõe um {@link Clock} único para a aplicação.
 *
 * <p>Todo código que precisa de "agora" injeta este bean em vez de chamar {@code Instant.now()},
 * o que permite congelar o tempo nos testes com {@code Clock.fixed(...)}.</p>
 */
@Configuration(proxyBeanMethods = false)
class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
```

```java
// path: src/main/java/com/acme/billing/shared/config/JacksonConfig.java
package com.acme.billing.shared.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.StreamWriteFeature;

/**
 * Ajustes globais do Jackson 3. O {@code JsonMapper} continua sendo o auto-configurado pelo Boot;
 * aqui só se acrescentam preferências que valem para toda a API.
 */
@Configuration(proxyBeanMethods = false)
class JacksonConfig {

    @Bean
    JsonMapperBuilderCustomizer billingJsonMapperCustomizer() {
        // BigDecimal sempre em notação decimal simples ("1234.5000"), nunca científica ("1.2345E+3").
        return builder -> builder.enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN);
    }
}
```

```java
// path: src/main/java/com/acme/billing/shared/config/OpenApiConfig.java
package com.acme.billing.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metadados globais da documentação OpenAPI (springdoc 3.x). */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    OpenAPI billingOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Billing API")
                        .version("1.0")
                        .description("API de faturamento: pedidos, faturas e pagamentos."))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                // Todas as operações exigem token, salvo as marcadas explicitamente no controller.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
```

```java
// path: src/main/java/com/acme/billing/shared/persistence/JpaConfig.java
package com.acme.billing.shared.persistence;

import java.util.Optional;
import java.time.Clock;
import java.time.Instant;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.context.SecurityContextHolder;

/** Habilita auditoria JPA (createdAt/updatedAt/createdBy/updatedBy) com o usuário autenticado. */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(auditorAwareRef = "auditorAware", dateTimeProviderRef = "auditingDateTimeProvider")
class JpaConfig {

    @Bean
    DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(Instant.now(clock));
    }

    @Bean
    AuditorAware<String> auditorAware() {
        // Usa o "subject" do token (identificador estável), nunca e-mail ou CPF, para não gravar PII.
        return () -> Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(auth -> auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken))
                .map(auth -> auth.getName())
                .or(() -> Optional.of("system"));
    }
}
```

```java
// path: src/main/java/com/acme/billing/shared/persistence/AuditableEntity.java
package com.acme.billing.shared.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Campos de auditoria compartilhados por todas as entidades.
 *
 * <p>Preenchidos automaticamente pelo {@link AuditingEntityListener}; nenhuma entidade os altera
 * manualmente. As colunas {@code created_*} são {@code updatable = false} para impedir sobrescrita.</p>
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class AuditableEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", length = 100, updatable = false)
    private @Nullable String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", length = 100)
    private @Nullable String updatedBy;
}
```

```java
// path: src/main/java/com/acme/billing/shared/web/PageResponse.java
package com.acme.billing.shared.web;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Envelope estável de paginação exposto pela API.
 *
 * <p>Nunca serializamos {@code Page<T>} diretamente: o JSON dele não é um contrato estável entre
 * versões do Spring Data. Este record é o contrato.</p>
 *
 * @param content itens da página
 * @param page índice da página (base 0)
 * @param size tamanho solicitado
 * @param totalElements total de itens em todas as páginas
 * @param totalPages total de páginas
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public PageResponse {
        content = List.copyOf(content);
    }

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
```

### 15.2 Error handling: exception hierarchy + Problem Details advice

```java
// path: src/main/java/com/acme/billing/shared/error/ErrorCode.java
package com.acme.billing.shared.error;

/**
 * Códigos de erro estáveis da API. O {@code slug} vira o sufixo do campo {@code type} do
 * Problem Details ({@code https://api.acme.com/problems/<slug>}) e a chave de mensagem
 * ({@code error.<slug>.title} / {@code error.<slug>.detail}) nos bundles i18n.
 */
public enum ErrorCode {
    FORBIDDEN("forbidden"),
    VALIDATION_FAILED("validation-failed"),
    MALFORMED_REQUEST("malformed-request"),
    RESOURCE_NOT_FOUND("resource-not-found"),
    ORDER_NOT_FOUND("order-not-found"),
    CUSTOMER_NOT_FOUND("customer-not-found"),
    ORDER_INVALID_STATE("order-invalid-state"),
    DUPLICATE_RESOURCE("duplicate-resource"),
    CONCURRENT_MODIFICATION("concurrent-modification"),
    BUSINESS_RULE_VIOLATION("business-rule-violation"),
    INTERNAL_ERROR("internal-error");

    private final String slug;

    ErrorCode(String slug) {
        this.slug = slug;
    }

    public String slug() {
        return slug;
    }
}
```

```java
// path: src/main/java/com/acme/billing/shared/error/DomainException.java
package com.acme.billing.shared.error;

import java.util.List;

/**
 * Raiz selada das exceções de negócio. Cada subtipo direto corresponde a um status HTTP; o
 * {@link ErrorCode} identifica o problema; {@code args} alimentam a mensagem localizada.
 *
 * <p>Ser {@code sealed} permite que o handler global faça um {@code switch} exaustivo: um novo
 * subtipo direto sem mapeamento não compila.</p>
 */
public abstract sealed class DomainException extends RuntimeException
        permits ResourceNotFoundException, BusinessRuleException, InvalidStateException, ConflictException {

    private final ErrorCode code;
    private final transient List<Object> args;

    protected DomainException(ErrorCode code, String message, Object... args) {
        super(message);
        this.code = code;
        this.args = List.of(args);
    }

    public ErrorCode code() {
        return code;
    }

    /** Argumentos para interpolação da mensagem localizada ({0}, {1}, ...). */
    public Object[] args() {
        return args.toArray();
    }
}
```

```java
// path: src/main/java/com/acme/billing/shared/error/ResourceNotFoundException.java
package com.acme.billing.shared.error;

/** Recurso inexistente → HTTP 404. Features criam subtipos específicos (ex.: {@code OrderNotFoundException}). */
public non-sealed class ResourceNotFoundException extends DomainException {

    public ResourceNotFoundException(ErrorCode code, String message, Object... args) {
        super(code, message, args);
    }
}
```

```java
// path: src/main/java/com/acme/billing/shared/error/BusinessRuleException.java
package com.acme.billing.shared.error;

/** Violação de regra de negócio que não é conflito de estado → HTTP 422 (ou 400, conforme decisão do projeto). */
public non-sealed class BusinessRuleException extends DomainException {

    public BusinessRuleException(ErrorCode code, String message, Object... args) {
        super(code, message, args);
    }
}
```

```java
// path: src/main/java/com/acme/billing/shared/error/InvalidStateException.java
package com.acme.billing.shared.error;

/** Transição de estado inválida (ex.: cancelar pedido entregue) → HTTP 409. */
public non-sealed class InvalidStateException extends DomainException {

    public InvalidStateException(ErrorCode code, String message, Object... args) {
        super(code, message, args);
    }
}
```

```java
// path: src/main/java/com/acme/billing/shared/error/ConflictException.java
package com.acme.billing.shared.error;

/** Conflito de unicidade ou de concorrência → HTTP 409. */
public final class ConflictException extends DomainException {

    public ConflictException(ErrorCode code, String message, Object... args) {
        super(code, message, args);
    }
}
```

```java
// path: src/main/java/com/acme/billing/shared/error/GlobalExceptionHandler.java
package com.acme.billing.shared.error;

import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.security.access.AccessDeniedException;
import java.sql.SQLException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Único ponto de conversão de exceções em respostas HTTP (RFC 9457 Problem Details).
 *
 * <p>Estende {@link ResponseEntityExceptionHandler} para reaproveitar o tratamento padrão do
 * Spring MVC (binding, validação, método não suportado etc.) e acrescenta os erros de domínio.
 * Controllers e serviços nunca montam corpo de erro.</p>
 */
@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String PROBLEM_TYPE_BASE = "https://api.acme.com/problems/";

    private final MessageSource messageSource;
    private final Clock clock;

    /** Erros de domínio: o subtipo selado define o status; o código define type/título/detalhe. */
    @ExceptionHandler(DomainException.class)
    ProblemDetail handleDomain(DomainException ex, WebRequest request) {
        // Switch exaustivo sobre a hierarquia selada: novo subtipo direto → erro de compilação aqui.
        HttpStatus status = switch (ex) {
            case ResourceNotFoundException _ -> HttpStatus.NOT_FOUND;
            case InvalidStateException _ -> HttpStatus.CONFLICT;
            case ConflictException _ -> HttpStatus.CONFLICT;
            case BusinessRuleException _ -> HttpStatus.UNPROCESSABLE_CONTENT;
        };
        log.warn("Domain error code={} status={}", ex.code(), status.value());
        return problem(status, ex.code(), ex.args(), request, null);
    }

    /** Violações em @RequestBody @Valid. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldViolation::from)
                .toList();
        var problem = problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, new Object[0], request, errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).headers(headers).body(problem);
    }

    /** Violações em @PathVariable/@RequestParam validados diretamente (Spring 6.1+). */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (ex.isForReturnValue()) {
            var problem = problem(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, new Object[0], request, null);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).headers(headers).body(problem);
        }
        List<FieldViolation> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldViolation(
                                result.getMethodParameter().getParameterName(),
                                messageSource.getMessage(error, LocaleContextHolder.getLocale()))))
                .toList();
        var problem = problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, new Object[0], request, errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).headers(headers).body(problem);
    }

    /** JSON malformado, tipo incompatível, enum desconhecido. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var problem = problem(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST, new Object[0], request, null);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).headers(headers).body(problem);
    }

    /** Validação programática (jakarta.validation.Validator) em serviços. */
    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        List<FieldViolation> errors = ex.getConstraintViolations().stream()
                .map(v -> new FieldViolation(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, new Object[0], request, errors);
    }

    /** Unique/FK violada: o banco é a única fonte confiável de unicidade. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex, WebRequest request) {
        // PostgreSQL: só unique violation é classificada genericamente como recurso duplicado.
        // Outras constraints precisam de mapeamento explícito por caso de uso.
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                return problem(HttpStatus.CONFLICT, ErrorCode.DUPLICATE_RESOURCE, new Object[0], request, null);
            }
        }
        log.error("Unmapped data integrity failure type={}", ex.getClass().getSimpleName());
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, new Object[0], request, null);
    }

    /** Falha de lock otimista (@Version): o cliente deve recarregar e reenviar. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex, WebRequest request) {
        return problem(HttpStatus.CONFLICT, ErrorCode.CONCURRENT_MODIFICATION, new Object[0], request, null);
    }

    /** Exceções da segurança de método não podem cair no tratamento genérico de erro 500. */
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        return problem(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, new Object[0], request, null);
    }

    /** Qualquer coisa não prevista: registra o tipo e responde 500 genérico (sem vazar detalhes). */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unexpected error type={} traceId={}", ex.getClass().getName(), MDC.get("traceId"));
        // Encaminhar diagnóstico detalhado apenas a um destino com redação e acesso restrito.
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, new Object[0], request, null);
    }

    // Monta o Problem Details com título/detalhe localizados e as propriedades extras padronizadas.
    private ProblemDetail problem(HttpStatus status, ErrorCode code, Object[] args, WebRequest request,
            @Nullable List<FieldViolation> errors) {
        Locale locale = LocaleContextHolder.getLocale();
        var problem = ProblemDetail.forStatus(status);
        problem.setType(URI.create(PROBLEM_TYPE_BASE + code.slug()));
        problem.setTitle(messageSource.getMessage("error." + code.slug() + ".title", null, status.getReasonPhrase(), locale));
        problem.setDetail(messageSource.getMessage("error." + code.slug() + ".detail", args, null, locale));
        problem.setProperty("code", code.name());
        problem.setProperty("timestamp", Instant.now(clock));
        // traceId vem do MDC (Micrometer Tracing/OTel); null fora de contexto de trace.
        problem.setProperty("traceId", MDC.get("traceId"));
        if (errors != null && !errors.isEmpty()) {
            problem.setProperty("errors", errors);
        }
        return problem;
    }

    /** Um erro de campo no array {@code errors} do Problem Details. */
    record FieldViolation(@Nullable String field, @Nullable String message) {

        static FieldViolation from(FieldError error) {
            return new FieldViolation(error.getField(), error.getDefaultMessage());
        }
    }
}
```

Message bundles below are complete for this example. The default `messages.properties` is required for Boot's message-source auto-configuration and supplies pt-BR fallback; `messages_en.properties` supports English. Set `spring.messages.fallback-to-system-locale=false`. Use the MVC locale resolver for request negotiation and the matching message source at security-filter boundaries. Field validation messages must never interpolate secret/rejected values.

```properties
# path: src/main/resources/messages.properties
app.validation.dateRange.invalid=A data inicial deve ser anterior ou igual à data final.
error.validation-failed.title=Requisição inválida
error.validation-failed.detail=Um ou mais campos são inválidos. Veja errors.
error.malformed-request.title=Requisição malformada
error.malformed-request.detail=O corpo da requisição não pôde ser lido.
error.order-not-found.title=Pedido não encontrado
error.order-not-found.detail=Pedido indisponível para esta consulta.
error.customer-not-found.title=Cliente não encontrado
error.customer-not-found.detail=Cliente indisponível para esta operação.
error.order-invalid-state.title=Estado incompatível
error.order-invalid-state.detail=O estado atual do pedido não permite esta operação.
error.duplicate-resource.title=Recurso duplicado
error.duplicate-resource.detail=Já existe um registro com os mesmos dados únicos.
error.concurrent-modification.title=Modificação concorrente
error.concurrent-modification.detail=O recurso foi alterado. Recarregue e tente novamente.
error.resource-not-found.title=Recurso não encontrado
error.resource-not-found.detail=Recurso indisponível para esta consulta.
error.business-rule-violation.title=Regra de negócio
error.business-rule-violation.detail=A operação não atende às regras do recurso.
error.internal-error.title=Erro interno
error.internal-error.detail=Ocorreu um erro inesperado. Informe o traceId ao suporte.
error.forbidden.title=Acesso negado
error.forbidden.detail=Você não tem permissão para esta operação.
error.unauthenticated.title=Não autenticado
error.unauthenticated.detail=Forneça um token Bearer válido.
```

```properties
# path: src/main/resources/messages_en.properties
app.validation.dateRange.invalid=The start date must be before or equal to the end date.
error.validation-failed.title=Invalid request
error.validation-failed.detail=One or more fields are invalid. See errors.
error.malformed-request.title=Malformed request
error.malformed-request.detail=The request body could not be read.
error.order-not-found.title=Order not found
error.order-not-found.detail=The order is unavailable for this request.
error.customer-not-found.title=Customer not found
error.customer-not-found.detail=The customer is unavailable for this operation.
error.order-invalid-state.title=Invalid state
error.order-invalid-state.detail=The current order state does not allow this operation.
error.duplicate-resource.title=Duplicate resource
error.duplicate-resource.detail=A record with these unique fields already exists.
error.concurrent-modification.title=Concurrent modification
error.concurrent-modification.detail=The resource changed. Reload and try again.
error.resource-not-found.title=Resource not found
error.resource-not-found.detail=The resource is unavailable for this request.
error.business-rule-violation.title=Business rule violation
error.business-rule-violation.detail=The operation does not satisfy resource rules.
error.internal-error.title=Internal error
error.internal-error.detail=An unexpected error occurred. Contact support with the traceId.
error.forbidden.title=Access denied
error.forbidden.detail=You do not have permission for this operation.
error.unauthenticated.title=Unauthenticated
error.unauthenticated.detail=Provide a valid Bearer token.
```

### 15.3 Domain: aggregate, child entity, status, feature exception, events

```java
// path: src/main/java/com/acme/billing/order/OrderStatus.java
package com.acme.billing.order;

import java.util.EnumSet;
import java.util.Set;

/** Ciclo de vida do pedido. As transições válidas ficam aqui, perto do dado que governam. */
public enum OrderStatus {
    PENDING,
    PAID,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    /** Estados a partir dos quais o pedido ainda pode ser cancelado. */
    private static final Set<OrderStatus> CANCELLABLE = EnumSet.of(PENDING, PAID);

    public boolean isCancellable() {
        return CANCELLABLE.contains(this);
    }
}
```

```java
// path: src/main/java/com/acme/billing/order/OrderNotFoundException.java
package com.acme.billing.order;

import com.acme.billing.shared.error.ErrorCode;
import com.acme.billing.shared.error.ResourceNotFoundException;
import java.util.UUID;

/** Pedido inexistente (404). Mensagem técnica em inglês; a mensagem ao cliente vem do bundle i18n. */
public class OrderNotFoundException extends ResourceNotFoundException {

    public OrderNotFoundException(UUID orderId) {
        super(ErrorCode.ORDER_NOT_FOUND, "Order not found: " + orderId, orderId);
    }
}
```

```java
// path: src/main/java/com/acme/billing/order/InvalidOrderStateException.java
package com.acme.billing.order;

import com.acme.billing.shared.error.ErrorCode;
import com.acme.billing.shared.error.InvalidStateException;
import java.util.UUID;

/** Transição inválida no ciclo de vida do pedido (409). */
public class InvalidOrderStateException extends InvalidStateException {

    public InvalidOrderStateException(UUID orderId, OrderStatus current, String attemptedAction) {
        super(ErrorCode.ORDER_INVALID_STATE,
                "Order %s is %s and cannot be %s".formatted(orderId, current, attemptedAction),
                orderId, current, attemptedAction);
    }
}
```

```java
// path: src/main/java/com/acme/billing/order/event/OrderCreatedEvent.java
package com.acme.billing.order.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Publicado na transação da criação; listeners AFTER_COMMIT só executam após o commit. Consumido por outras features (ex.: notificação,
 * análise de fraude) sem acoplá-las ao agregado.
 */
public record OrderCreatedEvent(UUID orderId, UUID customerId, BigDecimal totalAmount) {}
```

```java
// path: src/main/java/com/acme/billing/order/event/OrderCancelledEvent.java
package com.acme.billing.order.event;

import java.util.UUID;

/** Publicado na transação do cancelamento; consumidores que exigem commit usam AFTER_COMMIT. */
public record OrderCancelledEvent(UUID orderId, String reason) {}
```

```java
// path: src/main/java/com/acme/billing/order/OrderItem.java
package com.acme.billing.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.UuidGenerator;

/**
 * Item de um pedido. Entidade filha: só é criada e alterada por meio de {@link Order}.
 *
 * <p>Não tem repositório próprio nem é exposta fora do agregado.</p>
 */
@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class OrderItem {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @ToString.Include
    private UUID id;

    // Lado dono da associação. Sempre LAZY; nunca incluir em toString/equals.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false, length = 50)
    @ToString.Include
    private String sku;

    @Column(nullable = false)
    @ToString.Include
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private int position;

    // Package-private: só o agregado cria itens (invariantes ficam em Order.addItem).
    OrderItem(Order order, String sku, int quantity, BigDecimal unitPrice, int position) {
        this.order = order;
        this.position = position;
        this.sku = sku;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    /** Total da linha (quantidade × preço unitário), sem persistir: é derivado. */
    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrderItem other)) return false;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return OrderItem.class.hashCode();
    }
}
```

```java
// path: src/main/java/com/acme/billing/order/Order.java
package com.acme.billing.order;

import com.acme.billing.shared.persistence.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

/**
 * Agregado Pedido. Toda alteração de estado passa por métodos desta classe, que garantem os
 * invariantes (itens válidos, total consistente, transições permitidas).
 *
 * <p>Outros agregados (cliente) são referenciados por id, não por associação JPA: cada agregado é
 * carregado e persistido de forma independente.</p>
 */
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class Order extends AuditableEntity {

    /** Limite de negócio: pedidos maiores viram vários pedidos. */
    public static final int MAX_ITEMS = 100;

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @ToString.Include
    private UUID id;

    // Lock otimista: atualizações concorrentes geram OptimisticLockingFailureException → 409.
    @Version
    private long version;

    @Column(name = "customer_id", nullable = false)
    @ToString.Include
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @ToString.Include
    private OrderStatus status;

    // Desnormalizado de propósito: consultas de listagem não precisam carregar os itens.
    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC") // Posição preenchida no INSERT; a API não reordena itens.
    @BatchSize(size = 50)
    private List<OrderItem> items = new ArrayList<>();

    @Column(name = "cancellation_reason", length = 500)
    private @Nullable String cancellationReason;

    @Column(name = "cancelled_at")
    private @Nullable Instant cancelledAt;

    /**
     * Cria um pedido válido com os itens informados (padrão Static Factory: nome expressivo e
     * validação centralizada, em vez de um construtor público permissivo).
     *
     * @throws IllegalArgumentException se não houver itens ou se exceder {@link #MAX_ITEMS}
     */
    public static Order create(UUID customerId, List<OrderItemDraft> drafts) {
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(drafts, "drafts");
        if (drafts.isEmpty()) {
            throw new IllegalArgumentException("An order must have at least one item");
        }
        if (drafts.size() > MAX_ITEMS) {
            throw new IllegalArgumentException("An order cannot have more than " + MAX_ITEMS + " items");
        }
        var order = new Order();
        order.customerId = customerId;
        order.status = OrderStatus.PENDING;
        order.totalAmount = BigDecimal.ZERO;
        drafts.forEach(order::addItem);
        return order;
    }

    /** Cancela o pedido. Só é permitido em {@code PENDING} ou {@code PAID}. */
    public void cancel(String reason, Instant now) {
        Objects.requireNonNull(now, "now");
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new IllegalArgumentException("Invalid cancellation reason");
        }
        if (!status.isCancellable()) {
            throw new InvalidOrderStateException(id, status, "cancelled");
        }
        this.status = OrderStatus.CANCELLED;
        this.cancellationReason = reason;
        this.cancelledAt = now;
    }

    /** Visão somente leitura: a lista interna nunca sai do agregado. */
    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    // Único caminho para adicionar itens: mantém o total sempre consistente com as linhas.
    private void addItem(OrderItemDraft draft) {
        var item = new OrderItem(this, draft.sku(), draft.quantity(), draft.unitPrice(), items.size());
        items.add(item);
        totalAmount = totalAmount.add(item.lineTotal());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Order other)) return false;   // cobre proxies do Hibernate
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Order.class.hashCode();
    }

    /** Dados de um item ainda não persistido, já validados na borda (DTO) e normalizados aqui. */
    public record OrderItemDraft(String sku, int quantity, BigDecimal unitPrice) {

        public OrderItemDraft {
            Objects.requireNonNull(sku, "sku");
            Objects.requireNonNull(unitPrice, "unitPrice");
            if (sku.isBlank() || sku.length() > 50) throw new IllegalArgumentException("Invalid sku");
            if (quantity > 1_000) throw new IllegalArgumentException("quantity exceeds limit");
            if (unitPrice.scale() > 4 || unitPrice.precision() - unitPrice.scale() > 15) {
                throw new IllegalArgumentException("unitPrice exceeds precision");
            }
            if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
            if (unitPrice.signum() < 0) throw new IllegalArgumentException("unitPrice must not be negative");
            sku = sku.trim().toUpperCase(Locale.ROOT);
        }
    }
}
```

### 15.4 DTOs, repository, specifications, mapper

```java
// path: src/main/java/com/acme/billing/order/dto/CreateOrderRequest.java
package com.acme.billing.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;

/**
 * Corpo da requisição de criação de pedido.
 *
 * @param customerId cliente dono do pedido
 * @param items itens (1 a 100), cada um validado individualmente
 */
public record CreateOrderRequest(
        @NotNull UUID customerId,
        @NotEmpty @Size(max = 100) List<@NotNull @Valid OrderItemRequest> items) {

    public CreateOrderRequest {
        // A validação produz erros de campo para lista/elemento nulo, sem NPE no construtor.
        items = items == null ? null : Collections.unmodifiableList(new ArrayList<>(items));
    }
}
```

```java
// path: src/main/java/com/acme/billing/order/dto/OrderItemRequest.java
package com.acme.billing.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Item informado na criação do pedido. */
public record OrderItemRequest(
        @NotBlank @Size(max = 50) String sku,
        @Positive @Max(1_000) int quantity,
        @NotNull @DecimalMin("0.0000") @Digits(integer = 15, fraction = 4) BigDecimal unitPrice) {}
```

```java
// path: src/main/java/com/acme/billing/order/dto/CancelOrderRequest.java
package com.acme.billing.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Motivo do cancelamento, obrigatório para auditoria. */
public record CancelOrderRequest(@NotBlank @Size(max = 500) String reason) {}
```

```java
// path: src/main/java/com/acme/billing/order/dto/OrderResponse.java
package com.acme.billing.order.dto;

import com.acme.billing.order.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Representação completa de um pedido (endpoint de detalhe). */
public record OrderResponse(
        UUID id,
        UUID customerId,
        OrderStatus status,
        BigDecimal totalAmount,
        List<OrderItemResponse> items,
        @Nullable String cancellationReason,
        @Nullable Instant cancelledAt,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public OrderResponse {
        items = List.copyOf(items);
    }
}
```

```java
// path: src/main/java/com/acme/billing/order/dto/OrderItemResponse.java
package com.acme.billing.order.dto;

import java.math.BigDecimal;

/** Linha de um pedido na resposta de detalhe. */
public record OrderItemResponse(String sku, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {}
```

```java
// path: src/main/java/com/acme/billing/order/dto/OrderSummaryResponse.java
package com.acme.billing.order.dto;

import com.acme.billing.order.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Item de listagem. Também é o alvo da projeção JPQL ({@code select new ...}): a ordem e os tipos
 * dos componentes precisam bater com a query em {@code OrderRepository}.
 */
public record OrderSummaryResponse(UUID id, OrderStatus status, BigDecimal totalAmount, Instant createdAt) {}
```

```java
// path: src/main/java/com/acme/billing/order/dto/SearchOrdersRequest.java
package com.acme.billing.order.dto;

import com.acme.billing.order.OrderStatus;
import jakarta.validation.constraints.AssertTrue;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Filtros da listagem de pedidos. Bound a partir de query params (constructor binding do Spring MVC);
 * todos opcionais.
 */
public record SearchOrdersRequest(
        @Nullable UUID customerId,
        @Nullable OrderStatus status,
        @Nullable Instant createdFrom,
        @Nullable Instant createdTo) {

    /** Validação cruzada: intervalo de datas coerente. */
    @AssertTrue(message = "{app.validation.dateRange.invalid}")
    public boolean isDateRangeValid() {
        return createdFrom == null || createdTo == null || !createdFrom.isAfter(createdTo);
    }
}
```

```java
// path: src/main/java/com/acme/billing/order/OrderRepository.java
package com.acme.billing.order;

import com.acme.billing.order.dto.OrderSummaryResponse;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

/**
 * Persistência do agregado {@link Order}. Package-private: só o serviço da feature acessa.
 *
 * <p>Nenhuma regra de negócio aqui; apenas consultas nomeadas pela intenção.</p>
 */
interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    /** Carrega o pedido com os itens em uma única consulta (evita N+1 no detalhe). */
    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsByIdAndCustomerId(UUID id, UUID customerId);

    /**
     * Listagem por cliente projetada direto no record de resposta: não instancia entidades nem
     * carrega itens. A ordem dos argumentos do construtor deve bater com o record.
     */
    @Query("""
            select new com.acme.billing.order.dto.OrderSummaryResponse(
                o.id, o.status, o.totalAmount, o.createdAt)
            from Order o
            where o.customerId = :customerId
            """)
    Page<OrderSummaryResponse> findSummariesByCustomerId(UUID customerId, Pageable pageable);

    boolean existsByCustomerIdAndStatus(UUID customerId, OrderStatus status);
}
```

```java
// path: src/main/java/com/acme/billing/order/OrderSpecifications.java
package com.acme.billing.order;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

/**
 * Specifications (padrão Specification) reutilizáveis para filtros dinâmicos de {@link Order}.
 * Filtros opcionais podem ser irrestritos; o cliente é obrigatório e vem da identidade autenticada.
 *
 * <p>{@code Order_} é o metamodelo estático gerado pelo {@code hibernate-processor}.</p>
 */
final class OrderSpecifications {

    private OrderSpecifications() {}

    static Specification<Order> belongsTo(UUID customerId) {
        java.util.Objects.requireNonNull(customerId, "customerId");
        return (root, query, cb) -> cb.equal(root.get(Order_.customerId), customerId);
    }

    static Specification<Order> hasStatus(@Nullable OrderStatus status) {
        if (status == null) return Specification.unrestricted();
        return (root, query, cb) -> cb.equal(root.get(Order_.status), status);
    }

    static Specification<Order> createdBetween(@Nullable Instant from, @Nullable Instant to) {
        return (root, query, cb) -> {
            var path = root.get(Order_.createdAt);
            if (from != null && to != null) return cb.between(path, from, to);
            if (from != null) return cb.greaterThanOrEqualTo(path, from);
            if (to != null) return cb.lessThanOrEqualTo(path, to);
            return cb.conjunction();
        };
    }
}
```

```java
// path: src/main/java/com/acme/billing/order/OrderMapper.java
package com.acme.billing.order;

import com.acme.billing.order.dto.OrderItemRequest;
import com.acme.billing.order.dto.OrderItemResponse;
import com.acme.billing.order.dto.OrderResponse;
import com.acme.billing.order.dto.OrderSummaryResponse;

/**
 * Conversões entidade ↔ DTO da feature. Manual e sem estado: com records, MapStruct não se paga aqui.
 * Se o número de mapeamentos crescer, migrar para MapStruct com {@code unmappedTargetPolicy = ERROR}.
 */
final class OrderMapper {

    private OrderMapper() {}

    static Order.OrderItemDraft toDraft(OrderItemRequest request) {
        return new Order.OrderItemDraft(request.sku(), request.quantity(), request.unitPrice());
    }

    static OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getItems().stream().map(OrderMapper::toItemResponse).toList(),
                order.getCancellationReason(),
                order.getCancelledAt(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getVersion());
    }

    static OrderSummaryResponse toSummary(Order order) {
        return new OrderSummaryResponse(order.getId(), order.getStatus(), order.getTotalAmount(), order.getCreatedAt());
    }

    private static OrderItemResponse toItemResponse(OrderItem item) {
        return new OrderItemResponse(item.getSku(), item.getQuantity(), item.getUnitPrice(), item.lineTotal());
    }
}
```

```java
// path: src/main/java/com/acme/billing/shared/security/CurrentCustomer.java
package com.acme.billing.shared.security;

import java.util.UUID;

/** Identidade do cliente autenticado; não recebe ids do corpo, query string ou headers livres. */
public interface CurrentCustomer {
    UUID id();
}
```

```java
// path: src/main/java/com/acme/billing/shared/security/JwtCurrentCustomer.java
package com.acme.billing.shared.security;

import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Adapta o claim customer_id de um JWT já validado pelo Resource Server. */
@Component
final class JwtCurrentCustomer implements CurrentCustomer {
    @Override
    public UUID id() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new AccessDeniedException("Customer identity required");
        }
        Object claim = jwt.getClaim("customer_id");
        if (!(claim instanceof String value)
                || !value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw new AccessDeniedException("Invalid customer identity");
        }
        return UUID.fromString(value);
    }
}
```

### 15.5 Service and controller

This example serves customer-owned orders. The validated JWT must carry a canonical UUID string in `customer_id`; validate issuer and audience in the Resource Server configuration. `CustomerApi` is a port: a real project must supply its customer-feature implementation. There is no implicit operator bypass. Cross-customer administrative access needs an explicitly authorized use case and tests. Detail/cancel use owner-qualified queries and return 404 for absent or foreign orders; a conflicting customer filter/body is rejected with 403.

```java
// path: src/main/java/com/acme/billing/customer/CustomerApi.java
package com.acme.billing.customer;

import java.util.UUID;

/**
 * API pública da feature de clientes para as demais features. Outras features dependem só desta
 * interface (nunca do repositório ou da entidade de cliente).
 */
public interface CustomerApi {

    boolean exists(UUID customerId);
}
```

```java
// path: src/main/java/com/acme/billing/order/OrderService.java
package com.acme.billing.order;

import com.acme.billing.customer.CustomerApi;
import com.acme.billing.order.dto.CancelOrderRequest;
import com.acme.billing.order.dto.CreateOrderRequest;
import com.acme.billing.order.dto.OrderResponse;
import com.acme.billing.order.dto.OrderSummaryResponse;
import com.acme.billing.order.dto.SearchOrdersRequest;
import com.acme.billing.order.event.OrderCancelledEvent;
import com.acme.billing.order.event.OrderCreatedEvent;
import com.acme.billing.shared.error.ErrorCode;
import com.acme.billing.shared.error.ResourceNotFoundException;
import com.acme.billing.shared.web.PageResponse;
import com.acme.billing.shared.security.CurrentCustomer;
import org.springframework.security.access.AccessDeniedException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de pedidos. Um método público por caso de uso; a classe é somente leitura por
 * padrão e cada escrita abre sua própria transação.
 *
 * <p>Autorização grossa por escopo fica aqui ({@code @PreAuthorize}); a checagem de propriedade do
 * recurso (o cliente só vê os próprios pedidos) é aplicada nos filtros de consulta.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerApi customerApi;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final CurrentCustomer currentCustomer;

    /**
     * Cria um pedido para um cliente existente e publica {@link OrderCreatedEvent} dentro da transação.
     *
     * @throws ResourceNotFoundException se o cliente não existir
     */
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_orders:write')")
    public OrderResponse create(CreateOrderRequest request) {
        UUID customerId = currentCustomer.id();
        requireSameCustomer(request.customerId(), customerId);
        // Validação de negócio que depende de outra feature: não cabe no DTO nem no agregado.
        if (!customerApi.exists(customerId)) {
            throw new ResourceNotFoundException(ErrorCode.CUSTOMER_NOT_FOUND,
                    "Customer not found: " + request.customerId(), request.customerId());
        }

        var order = Order.create(customerId,
                request.items().stream().map(OrderMapper::toDraft).toList());
        orderRepository.save(order);

        // A publicação é imediata; só listeners com AFTER_COMMIT aguardam um commit bem-sucedido.
        eventPublisher.publishEvent(new OrderCreatedEvent(order.getId(), order.getCustomerId(), order.getTotalAmount()));
        log.info("Order created orderId={} customerId={} items={} total={}",
                order.getId(), order.getCustomerId(), order.getItems().size(), order.getTotalAmount());
        return OrderMapper.toResponse(order);
    }

    /** @throws OrderNotFoundException se não existir */
    @PreAuthorize("hasAuthority('SCOPE_orders:read')")
    public OrderResponse findById(UUID orderId) {
        return orderRepository.findWithItemsByIdAndCustomerId(orderId, currentCustomer.id())
                .map(OrderMapper::toResponse)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    /** Listagem paginada com filtros opcionais compostos por Specifications. */
    @PreAuthorize("hasAuthority('SCOPE_orders:read')")
    public PageResponse<OrderSummaryResponse> search(SearchOrdersRequest filter, Pageable pageable) {
        UUID customerId = currentCustomer.id();
        if (filter.customerId() != null) {
            requireSameCustomer(filter.customerId(), customerId);
        }
        var spec = OrderSpecifications.belongsTo(customerId)
                .and(OrderSpecifications.hasStatus(filter.status()))
                .and(OrderSpecifications.createdBetween(filter.createdFrom(), filter.createdTo()));
        // findAll(spec, pageable) não carrega itens (LAZY) e o mapper de resumo não os toca: sem N+1.
        var page = orderRepository.findAll(spec, pageable).map(OrderMapper::toSummary);
        return PageResponse.from(page);
    }

    /**
     * Cancela o pedido. A regra de transição está no agregado; o UPDATE acontece por dirty checking no commit.
     *
     * @throws OrderNotFoundException se não existir
     * @throws InvalidOrderStateException se o estado atual não permitir cancelamento
     */
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_orders:write')")
    public OrderResponse cancel(UUID orderId, CancelOrderRequest request) {
        var order = orderRepository.findWithItemsByIdAndCustomerId(orderId, currentCustomer.id())
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        order.cancel(request.reason(), Instant.now(clock));
        // A resposta inclui versão/auditoria atualizadas; força o flush antes de montar o DTO.
        orderRepository.flush();

        eventPublisher.publishEvent(new OrderCancelledEvent(order.getId(), request.reason()));
        log.info("Order cancelled orderId={}", order.getId());
        return OrderMapper.toResponse(order);
    }

    private static void requireSameCustomer(UUID requested, UUID authenticated) {
        if (!authenticated.equals(requested)) {
            throw new AccessDeniedException("Customer access denied");
        }
    }

}
```

```java
// path: src/main/java/com/acme/billing/order/OrderController.java
package com.acme.billing.order;

import com.acme.billing.order.dto.CancelOrderRequest;
import com.acme.billing.order.dto.CreateOrderRequest;
import com.acme.billing.order.dto.OrderResponse;
import com.acme.billing.order.dto.OrderSummaryResponse;
import com.acme.billing.order.dto.SearchOrdersRequest;
import com.acme.billing.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Endpoints REST de pedidos. Só HTTP: converte requisição em chamada de serviço e resultado em
 * resposta. Erros são tratados pelo {@code GlobalExceptionHandler}.
 */
@RestController
@RequestMapping(path = "/api/orders", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Gestão de pedidos")
class OrderController {

    private final OrderService orderService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Cria um pedido")
    public ResponseEntity<OrderResponse> create(@RequestBody @Valid CreateOrderRequest request,
            UriComponentsBuilder uriBuilder) {
        var response = orderService.create(request);
        var location = uriBuilder.path("/api/orders/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Consulta um pedido pelo id")
    public OrderResponse findById(@PathVariable UUID orderId) {
        return orderService.findById(orderId);
    }

    @GetMapping
    @Operation(summary = "Lista pedidos com filtros e paginação")
    public PageResponse<OrderSummaryResponse> search(
            @ParameterObject @Valid SearchOrdersRequest filter,
            @ParameterObject @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return orderService.search(filter, pageable);
    }

    @PostMapping(path = "/{orderId}/cancellation", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Cancela um pedido")
    public OrderResponse cancel(@PathVariable UUID orderId, @RequestBody @Valid CancelOrderRequest request) {
        return orderService.cancel(orderId, request);
    }
}
```

### 15.6 Security configuration (JWT resource server, Problem Details on 401/403)

This is an API-only policy. When bundling a React SPA in the same server (for example layout D2 in `STRUCTURE.md`), explicitly add the required web/static-route policy while preserving API/Actuator protection and final coverage. Do not remove the fallback chain to make the UI load. Keep API and missing-asset 404 behavior in the SPA resolver tests.

```java
// path: src/main/java/com/acme/billing/shared/security/SecurityConfig.java
package com.acme.billing.shared.security;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Segurança da API: stateless, Bearer JWT validado contra o issuer configurado em
 * {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}, negação por padrão.
 *
 * <p>Três cadeias ordenadas: API, Actuator e negação de qualquer rota restante.</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http,
            ProblemDetailAuthenticationEntryPoint entryPoint,
            ProblemDetailAccessDeniedHandler deniedHandler) throws Exception {
        http
                .securityMatcher("/api/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()
                        // Tudo o mais exige token válido; autorização fina fica em @PreAuthorize nos serviços.
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                // API sem sessão nem cookie: não há vetor de CSRF, por isso desabilitar é correto aqui.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http,
            ProblemDetailAuthenticationEntryPoint entryPoint, ProblemDetailAccessDeniedHandler deniedHandler) throws Exception {
        http
                .securityMatcher("/actuator/**")
                .authorizeHttpRequests(auth -> auth
                        // Permissão explícita para os probes escolhidos; os detalhes continuam restritos.
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().hasAuthority("SCOPE_ops:read"))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain fallbackSecurityFilterChain(HttpSecurity http,
            ProblemDetailAuthenticationEntryPoint entryPoint, ProblemDetailAccessDeniedHandler deniedHandler)
            throws Exception {
        http.authorizeHttpRequests(auth -> auth.anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    /** Mapeia o claim {@code scope} do JWT para authorities {@code SCOPE_xxx}, usadas em @PreAuthorize. */
    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("scope");
        authorities.setAuthorityPrefix("SCOPE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        var config = new CorsConfiguration();
        // Origens explícitas vindas da configuração; nunca "*" com credenciais.
        config.setAllowedOrigins(corsProperties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id", "X-API-Version"));
        config.setExposedHeaders(List.of("Location", "ETag", "X-Request-Id"));
        config.setAllowCredentials(false); // Bearer explícito; este exemplo não autentica por cookie.
        config.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

}
```

```java
// path: src/main/java/com/acme/billing/shared/security/CorsProperties.java
package com.acme.billing.shared.security;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Origens permitidas para CORS (prefixo {@code app.cors}).
 *
 * @param allowedOrigins lista explícita, ex.: {@code https://app.acme.com}
 */
@Validated
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(@NotEmpty List<String> allowedOrigins) {}
```

```java
// path: src/main/java/com/acme/billing/shared/security/ProblemDetailAuthenticationEntryPoint.java
package com.acme.billing.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.context.MessageSource;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Responde 401 no mesmo formato Problem Details do restante da API. Não revela por que o token
 * falhou (expirado, assinatura, ausente) além do necessário.
 */
@Component
@RequiredArgsConstructor
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final JsonMapper jsonMapper;
    private final MessageSource messageSource;
    private final Clock clock;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        var problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problem.setType(URI.create("https://api.acme.com/problems/unauthenticated"));
        problem.setTitle(messageSource.getMessage("error.unauthenticated.title", null, request.getLocale()));
        problem.setDetail(messageSource.getMessage("error.unauthenticated.detail", null, request.getLocale()));
        problem.setInstance(URI.create(request.getRequestURI()));

        problem.setProperty("code", "UNAUTHENTICATED");
        problem.setProperty("timestamp", Instant.now(clock));
        problem.setProperty("traceId", MDC.get("traceId"));

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), problem);
    }
}
```

```java
// path: src/main/java/com/acme/billing/shared/security/ProblemDetailAccessDeniedHandler.java
package com.acme.billing.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.context.MessageSource;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Responde 403 em Problem Details. */
@Component
@RequiredArgsConstructor
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    private final JsonMapper jsonMapper;
    private final MessageSource messageSource;
    private final Clock clock;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        var problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problem.setType(URI.create("https://api.acme.com/problems/forbidden"));
        problem.setTitle(messageSource.getMessage("error.forbidden.title", null, request.getLocale()));
        problem.setDetail(messageSource.getMessage("error.forbidden.detail", null, request.getLocale()));
        problem.setInstance(URI.create(request.getRequestURI()));

        problem.setProperty("code", "FORBIDDEN");
        problem.setProperty("timestamp", Instant.now(clock));
        problem.setProperty("traceId", MDC.get("traceId"));

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), problem);
    }
}
```

### 15.7 Flyway migration, compose, Dockerfile, test data builder

```sql
-- path: src/main/resources/db/migration/V202609101200__create_orders.sql
-- Pedidos e itens. Tipos explícitos, constraints nomeadas, índices para FKs e colunas filtradas/ordenadas.

create table orders (
    id                  uuid            not null,
    version             bigint          not null default 0,
    customer_id         uuid            not null,
    status              varchar(20)     not null,
    total_amount        numeric(19, 4)  not null,
    cancellation_reason varchar(500),
    cancelled_at        timestamptz,
    created_at          timestamptz     not null,
    updated_at          timestamptz     not null,
    created_by          varchar(100),
    updated_by          varchar(100),
    constraint pk_orders primary key (id),
    constraint ck_orders_status check (status in ('PENDING', 'PAID', 'SHIPPED', 'DELIVERED', 'CANCELLED')),
    constraint ck_orders_total_amount check (total_amount >= 0)
);

-- Listagem por cliente ordenada por data (com desempate por id, como a API faz).
create index ix_orders_customer_id_created_at on orders (customer_id, created_at desc, id desc);
create index ix_orders_status on orders (status);

create table order_items (
    id          uuid            not null,
    order_id    uuid            not null,
    position    integer         not null,
    sku         varchar(50)     not null,
    quantity    integer         not null,
    unit_price  numeric(19, 4)  not null,
    constraint pk_order_items primary key (id),
    constraint fk_order_items_order foreign key (order_id) references orders (id),
    constraint uq_order_items_order_position unique (order_id, position),
    constraint ck_order_items_quantity check (quantity > 0),
    constraint ck_order_items_unit_price check (unit_price >= 0)
);

-- A constraint uq_order_items_order_position já cria índice com order_id como prefixo.
```

```yaml
# path: compose.yaml
# Usado em desenvolvimento; credenciais descartáveis, nunca de produção.
services:
  postgres:
    image: postgres:18-alpine
    environment:
      POSTGRES_DB: billing
      POSTGRES_USER: billing
      POSTGRES_PASSWORD: billing
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U billing"]
      interval: 5s
      timeout: 3s
      retries: 10
```

```dockerfile
# path: Dockerfile
# Compilar o JAR antes: ./mvnw verify. Fixar o digest aprovado no projeto consumidor.
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app --no-create-home app
COPY --chown=app:app target/app.jar /app/application.jar
USER app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Duser.timezone=UTC"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
```

(Set Maven `build.finalName` to `app`, or adapt the Dockerfile source path to the actual artifact. The plain JRE image is the default reference; optional layering/AOT requires separate successful build and startup checks.)

```java
// path: src/test/java/com/acme/billing/order/OrderTestData.java
package com.acme.billing.order;

import com.acme.billing.order.dto.CreateOrderRequest;
import com.acme.billing.order.dto.OrderItemRequest;
import com.acme.billing.order.dto.OrderResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Object Mother da feature: fixtures nomeados pela intenção, com valores fixos (determinismo).
 * Só os testes desta feature usam esta classe.
 */
public final class OrderTestData {

    public static final UUID CUSTOMER_ID = UUID.fromString("0192a1b2-0000-7000-8000-000000000001");
    static final Instant FIXED_NOW = Instant.parse("2026-09-10T12:00:00Z");

    private OrderTestData() {}

    public static CreateOrderRequest createRequest() {
        return new CreateOrderRequest(CUSTOMER_ID,
                List.of(new OrderItemRequest("ABC-1", 2, new BigDecimal("10.0000"))));
    }

    public static Order pending() {
        var order = Order.create(CUSTOMER_ID,
                List.of(new Order.OrderItemDraft("ABC-1", 2, new BigDecimal("10.0000"))));
        // Id é atribuído pelo Hibernate ao persistir; em testes unitários simulamos via reflexão.
        ReflectionTestUtils.setField(order, "id", UUID.fromString("0192a1b2-0000-7000-8000-000000000010"));
        return order;
    }

    public static Order delivered() {
        var order = pending();
        ReflectionTestUtils.setField(order, "status", OrderStatus.DELIVERED);
        return order;
    }

    public static OrderResponse response(UUID id) {
        return new OrderResponse(id, CUSTOMER_ID, OrderStatus.PENDING, new BigDecimal("20.0000"),
                List.of(), null, null, FIXED_NOW, FIXED_NOW, 0L);
    }
}
```

---

### 15.8 Assembly, evidence and revision notes

Part 15 supplies complete source files for one example feature, not a deployable business product: `CustomerApi` needs the consuming project's real customer implementation, and JWT/CORS/database inputs must be configured. Select the dependencies in Part 1.3, enable Lombok/Hibernate annotation processors (Part 11.2), and include the default message bundle. Tests in §§10.3/10.5/10.7 are complete files with explicit test-only collaborators. Do not substitute a mocked service for the ownership integration suite.

An optional `reference-project/` accompanies revision 1.1 with the extracted files, a concrete Boot 4.1.1 POM and the tests. Its Maven fixture is a way to exercise the reference, not a replacement for generating a real application with official project tooling. `mvn test` runs unit/slice/architecture tests; `mvn verify` also runs the PostgreSQL integration suite. The fixture has no downloaded Maven wrapper; use a locally available compatible Maven, or generate the official wrapper before adopting it into a project. The guide remains usable without the fixture.

**Verification status, 2026-09-13:** the documentation and assembled source/configuration were inspected statically. This editing environment provides a Java 17 runtime but no JDK 25 compiler, Maven or Docker; external dependency download attempts timed out. Compilation, test execution, database migrations, container startup and real IdP validation were **not run**. Syntax/reference checks do not establish those outcomes. Re-run the relevant gates in the consuming environment before treating these templates as build-verified.

Primary sources used for the substantive corrections:

- [Spring Security multiple chains](https://docs.spring.io/spring-security/reference/servlet/configuration/java.html): matcher scope and the need to cover unmatched requests. The owner-qualified service/query is this guide's implementation of its stated access policy.
- [Jakarta Persistence 3.2 Embeddable](https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/embeddable): record embeddables and constructor requirements.
- [Hibernate 6 migration guide](https://docs.hibernate.org/orm/6.0/migration-guide/): entity deduplication and SQL pass-through of explicit `distinct`.
- [Flyway script configuration](https://documentation.red-gate.com/fd/script-configuration-277578847.html): matching `.sql.conf` sidecar and `executeInTransaction`.
- [Java 25 ScopedValue](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ScopedValue.html): thread-local bindings and structured-task inheritance.
- [Receita Federal CNPJ FAQ](https://www.gov.br/receitafederal/pt-br/centrais-de-conteudo/publicacoes/perguntas-e-respostas/cnpj/cnpj-alfanumerico.pdf) and [check-digit method](https://normas.receita.fazenda.gov.br/sijut2consulta/anexoOutros.action?idArquivoBinario=76203): alphanumeric CNPJ syntax, preservation of numeric registrations and validation.
- [Boot requirements](https://docs.spring.io/spring-boot/system-requirements.html) and [managed coordinates](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html): one compatible BOM-owned stack. These pages change; use the selected release's versioned documentation for future reviews.
- [springdoc compatibility](https://springdoc.org/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot), [ArchUnit installation](https://www.archunit.org/userguide/html/000_Index.html), [MapStruct](https://mapstruct.org/): explicitly pinned non-BOM reference versions.
- [Jackson StreamWriteFeature](https://github.com/FasterXML/jackson-core/blob/3.x/src/main/java/tools/jackson/core/StreamWriteFeature.java): correct enum for `WRITE_BIGDECIMAL_AS_PLAIN`.

**Correction, 2026-09-14:** §0.1 now lists repository instructions and installed-version documentation, matching the applicability contract at the top of this file.

Revalidate version-sensitive claims on upgrades. Stable design rules, dated source observations and results from executed builds are distinct forms of evidence.

---

*End of guide. Keep this file under version control next to the code; update Part 1 whenever the stack is upgraded, and record deliberate deviations at the top of the section they affect.*
