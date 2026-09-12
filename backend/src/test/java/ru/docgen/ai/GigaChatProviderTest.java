package ru.docgen.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Разбор токен-ответа GigaChat (Сбер) и логика автообновления access-токена.
 */
class GigaChatProviderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void parsesAccessTokenAndExpiresIn() throws Exception {
        var node = MAPPER.readTree("""
                {"access_token":"tok-123","expires_in":3600,"token_type":"Bearer"}
                """);
        var token = GigaChatProvider.parseTokenResponse(node);
        assertEquals("tok-123", token.value);
        assertNotNull(token.expiresAt, "expires_in -> expiresAt должен определиться");
        long delta = Duration.between(Instant.now(), token.expiresAt).getSeconds();
        assertTrue(delta > 3550 && delta < 3650, "expiresAt ≈ now + 3600s, реально " + delta);
    }

    @Test
    void parsesTokenNestedInData() throws Exception {
        var node = MAPPER.readTree("""
                {"data":{"access_token":"nested-token","expires_at":4102444800}}
                """);
        var token = GigaChatProvider.parseTokenResponse(node);
        assertEquals("nested-token", token.value);
        assertTrue(token.expiresAt != null, "expires_at в сек. должен распарситься");
    }

    @Test
    void parsesExpiresAtInMilliseconds() throws Exception {
        var node = MAPPER.createObjectNode();
        node.put("expires_at", Instant.now().plus(Duration.ofHours(2)).toEpochMilli());
        Instant parsed = GigaChatProvider.parseExpiry(node);
        assertNotNull(parsed);
        long delta = Math.abs(Duration.between(Instant.now(), parsed).getSeconds());
        assertTrue(delta > 7100 && delta < 7300, "миллисекунды распознаны как epoch-ms, delta=" + delta);
    }

    @Test
    void parsesExpiresAtIsoString() throws Exception {
        String iso = Instant.now().plusSeconds(5000).toString();
        Instant parsed = GigaChatProvider.parseExpiry(MAPPER.readTree("{\"expires_at\":\"" + iso + "\"}"));
        assertNotNull(parsed);
        long delta = Math.abs(Duration.between(Instant.now(), parsed).getSeconds());
        assertTrue(delta > 4900 && delta < 5100, "ISO-строка: delta=" + delta);
    }

    @Test
    void missingAccessTokenThrows() throws Exception {
        var node = MAPPER.readTree("{\"error\":\"invalid_client\"}");
        assertThrows(AIUnavailableException.class, () ->
                GigaChatProvider.parseTokenResponse(node));
    }

    @Test
    void unknownExpiryLeavesExpiresAtNull() throws Exception {
        var node = MAPPER.readTree("{\"access_token\":\"tok\"}");
        assertNull(GigaChatProvider.parseTokenResponse(node).expiresAt);
    }

    @Test
    void tokenExpiresAtBoundary() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        GigaChatProvider.Token t = new GigaChatProvider.Token("tok", now);
        assertTrue(t.isExpired(now.plusSeconds(1)));
        assertFalse(t.isExpired(now.minusSeconds(1)));
    }

    @Test
    void tokenNeedsRefreshInsideMargin() {
        Instant expires = Instant.parse("2026-01-01T00:09:00Z");
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        GigaChatProvider.Token t = new GigaChatProvider.Token("tok", expires);
        // До истечения 9 минут — меньше запаса 10 минут → пора обновлять
        assertTrue(t.needsRefresh(now));
    }

    @Test
    void tokenNoRefreshFarFromExpiry() {
        Instant expires = Instant.parse("2026-01-01T01:00:00Z");
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        GigaChatProvider.Token t = new GigaChatProvider.Token("tok", expires);
        assertFalse(t.needsRefresh(now), "За час до истечения обновлять не нужно");
    }

    @Test
    void tokenWithoutExpiryNeverAutoRefreshs() {
        GigaChatProvider.Token t = new GigaChatProvider.Token("tok", null);
        assertFalse(t.needsRefresh(Instant.now()), "Без срока истечения — только retry по 401/403");
        assertFalse(t.isExpired(Instant.now()));
    }
}