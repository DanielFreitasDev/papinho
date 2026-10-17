package io.github.labdev2025cmd.papinho.shared.error;

import org.apache.catalina.Container;
import org.apache.catalina.core.StandardHost;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Erros recusados antes do Spring MVC também em Problem Details (SG-01, SG-02). O que o StrictHttpFirewall recusa
 * vira {@code sendError(400)} e chega ao {@link ProblemErrorController}; o que o Tomcat recusa sozinho passa pela
 * {@link ProblemErrorReportValve}.
 */
@Configuration(proxyBeanMethods = false)
class ErrorHandlingConfig {

    /** A página de erro do próprio Tomcat vira Problem Details, sem a versão do servidor. */
    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> problemErrorReportValve() {
        return new WebServerFactoryCustomizer<>() {
            @Override
            public void customize(TomcatServletWebServerFactory factory) {
                factory.addContextCustomizers(context -> {
                    Container parent = context.getParent();
                    if (parent instanceof StandardHost host) {
                        host.setErrorReportValveClass(ProblemErrorReportValve.class.getName());
                    }
                });
            }
        };
    }
}
