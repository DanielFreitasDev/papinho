package io.github.labdev2025cmd.papinho;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/** Verifica as fronteiras dos módulos do Spring Modulith (ADR 0005) e gera a documentação dos módulos. */
class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(PapinhoApplication.class);

    @Test
    @DisplayName("módulos respeitam as fronteiras e não têm ciclos")
    void shouldRespectModuleBoundaries() {
        modules.verify();
    }

    @Test
    @DisplayName("gera a documentação dos módulos em target/spring-modulith-docs")
    void shouldWriteModuleDocumentation() {
        new Documenter(modules).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
    }
}
