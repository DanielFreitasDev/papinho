package io.github.labdev2025cmd.papinho.linkpreview.internal;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Quais endereços o servidor pode buscar (MD-04). Recusa tudo que não é internet pública: loopback, rede local
 * (RFC 1918), link-local (inclusive o 169.254.169.254 de metadados de nuvem), CGNAT, multicast, faixas
 * reservadas e de documentação, IPv6 de site local/único e endereços IPv4 embutidos em IPv6.
 */
public interface AddressPolicy {

    boolean allowed(InetAddress address);

    /** Portas além de 80 e 443 (só em testes, contra um servidor local). */
    default boolean allowsPort(int port) {
        return false;
    }

    /** Só endereços públicos (padrão). */
    AddressPolicy PUBLIC_ONLY = AddressPolicy::isPublic;

    static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            return isPublicIpv4(bytes);
        }
        if (address instanceof Inet6Address) {
            int first = bytes[0] & 0xFF;
            // fc00::/7 (únicos locais) e 2001:db8::/32 (documentação).
            if ((first & 0xFE) == 0xFC || (first == 0x20 && (bytes[1] & 0xFF) == 0x01 && (bytes[2] & 0xFF) == 0x0D
                    && (bytes[3] & 0xFF) == 0xB8)) {
                return false;
            }
            // ::ffff:a.b.c.d, ::a.b.c.d e 64:ff9b::/96 carregam um IPv4: vale a regra do IPv4.
            boolean zeroPrefix = true;
            for (int i = 0; i < 10; i++) {
                zeroPrefix &= bytes[i] == 0;
            }
            boolean mapped = zeroPrefix && ((bytes[10] == (byte) 0xFF && bytes[11] == (byte) 0xFF)
                    || (bytes[10] == 0 && bytes[11] == 0));
            boolean nat64 = bytes[0] == 0 && bytes[1] == 0x64 && bytes[2] == (byte) 0xFF && bytes[3] == (byte) 0x9B;
            if (mapped || nat64) {
                byte[] v4 = {bytes[12], bytes[13], bytes[14], bytes[15]};
                try {
                    InetAddress embedded = InetAddress.getByAddress(v4);
                    return isPublic(embedded);
                } catch (UnknownHostException impossible) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private static boolean isPublicIpv4(byte[] b) {
        int a0 = b[0] & 0xFF;
        int a1 = b[1] & 0xFF;
        int a2 = b[2] & 0xFF;
        return !(a0 == 0 // 0.0.0.0/8
                || a0 == 10 || a0 == 127 // privado e loopback
                || (a0 == 100 && a1 >= 64 && a1 <= 127) // CGNAT 100.64.0.0/10
                || (a0 == 169 && a1 == 254) // link-local e metadados de nuvem
                || (a0 == 172 && a1 >= 16 && a1 <= 31) || (a0 == 192 && a1 == 168)
                || (a0 == 192 && a1 == 0 && (a2 == 0 || a2 == 2)) // IETF e documentação
                || (a0 == 198 && (a1 == 18 || a1 == 19)) // testes de desempenho
                || (a0 == 198 && a1 == 51 && a2 == 100) || (a0 == 203 && a1 == 0 && a2 == 113)
                || a0 >= 224); // multicast, reservado e broadcast
    }
}
