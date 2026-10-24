package com.jaxon.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AuthorizedScopeNormalizerTest {

    @Test
    void normalizesNamesAndCanonicalNetworkAddresses() {
        AuthorizedScopeNormalizer.Name name = AuthorizedScopeNormalizer.normalizeName("\u2028 Red\tＯficina \u2028");

        assertThat(name.display()).isEqualTo("Red Oficina");
        assertThat(name.normalized()).isEqualTo("red oficina");
        assertThat(AuthorizedScopeNormalizer.normalizeCidr("192.0.2.0/24")).isEqualTo("192.0.2.0/24");
        assertThat(AuthorizedScopeNormalizer.normalizeCidr("2001:0DB8:0:0::/64")).isEqualTo("2001:db8::/64");
        assertThat(AuthorizedScopeNormalizer.normalizeName("ΟΣ").normalized())
                .isEqualTo(AuthorizedScopeNormalizer.normalizeName("Ος").normalized());
        assertThat(AuthorizedScopeNormalizer.normalizeName("Straße").normalized())
                .isEqualTo(AuthorizedScopeNormalizer.normalizeName("STRAẞE").normalized());
    }

    @Test
    void rejectsHostBitsAmbiguousIpv4AndNonLiteralIpv6() {
        assertThatThrownBy(() -> AuthorizedScopeNormalizer.normalizeCidr("192.0.2.1/24"))
                .isInstanceOf(InvalidAuthorizedScopeException.class);
        assertThatThrownBy(() -> AuthorizedScopeNormalizer.normalizeCidr("192.168.001.0/24"))
                .isInstanceOf(InvalidAuthorizedScopeException.class);
        assertThatThrownBy(() -> AuthorizedScopeNormalizer.normalizeCidr("localhost/128"))
                .isInstanceOf(InvalidAuthorizedScopeException.class);
        assertThatThrownBy(() -> AuthorizedScopeNormalizer.normalizeCidr("2001:db8::1/64"))
                .isInstanceOf(InvalidAuthorizedScopeException.class);
    }

    @Test
    void rejectsNamesWhoseCaseFoldExceedsThePersistenceLimit() {
        assertThatThrownBy(() -> AuthorizedScopeNormalizer.normalizeName("İ".repeat(200)))
                .isInstanceOf(InvalidAuthorizedScopeException.class);
    }
}
