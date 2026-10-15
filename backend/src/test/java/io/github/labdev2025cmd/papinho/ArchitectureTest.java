package io.github.labdev2025cmd.papinho;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

/** Regras de arquitetura do JAVA.md 5.3 que o Spring Modulith não cobre. */
@AnalyzeClasses(packagesOf = PapinhoApplication.class, importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule noFieldInjection = noFields().should().beAnnotatedWith(Autowired.class)
            .because("injeção é sempre pelo construtor");

    @ArchTest
    static final ArchRule noLegacyDates = noClasses().that().resideOutsideOfPackage("..pki.internal..")
            .should().dependOnClassesThat()
            .belongToAnyOf(java.util.Date.class, java.util.Calendar.class)
            .orShould().dependOnClassesThat().haveFullyQualifiedName("java.sql.Timestamp")
            .because("datas usam java.time; Date só aparece na fronteira com o BouncyCastle (pki.internal)");

    @ArchTest
    static final ArchRule controllersDoNotUseRepositories = noClasses().that()
            .areAnnotatedWith(RestController.class)
            .should().dependOnClassesThat().areAssignableTo(org.springframework.data.repository.Repository.class)
            .orShould().dependOnClassesThat().areAnnotatedWith(Repository.class)
            .because("controllers falam só com serviços")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule controllersAreNamed = ArchRuleDefinition.classes().that()
            .areAnnotatedWith(RestController.class).should().haveSimpleNameEndingWith("Controller")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule servicesDoNotDependOnWeb = noClasses().that().haveSimpleNameEndingWith("Service")
            .should().dependOnClassesThat().resideInAnyPackage("jakarta.servlet..")
            .because("serviços não conhecem HTTP")
            .allowEmptyShould(true);
}
