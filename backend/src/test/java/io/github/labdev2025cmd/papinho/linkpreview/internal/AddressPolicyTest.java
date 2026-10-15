package io.github.labdev2025cmd.papinho.linkpreview.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Endereços que a prévia de links nunca pode buscar (MD-04, SSRF). */
class AddressPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "127.255.0.9", "0.0.0.0", "10.1.2.3", "172.16.0.1", "172.31.255.255",
        "192.168.0.10", "169.254.169.254", "100.64.0.1", "100.127.1.1", "192.0.2.5", "198.18.0.1", "203.0.113.7",
        "224.0.0.1", "255.255.255.255", "::1", "::", "fe80::1", "fc00::1", "fd12:3456::1", "::ffff:127.0.0.1",
        "::ffff:10.0.0.1", "64:ff9b::a00:1", "64:ff9b::7f00:1", "2001:db8::1", "ff02::1"})
    @DisplayName("loopback, rede local, link-local, metadados, CGNAT, reservados e IPv4 embutido são recusados")
    void shouldBlockNonPublicAddresses(String literal) {
        assertThat(AddressPolicy.isPublic(InetAddress.ofLiteral(literal))).as(literal).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"93.184.215.14", "8.8.8.8", "1.1.1.1", "172.32.0.1", "100.128.0.1",
        "2606:4700:4700::1111", "2a00:1450:4001:80b::200e"})
    @DisplayName("endereços públicos passam")
    void shouldAllowPublicAddresses(String literal) {
        assertThat(AddressPolicy.isPublic(InetAddress.ofLiteral(literal))).as(literal).isTrue();
    }
}
