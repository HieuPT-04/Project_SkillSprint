package com.skillsprint.service.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DefaultIdenticonTest {

    @Test
    void createsStableDataUriForTheSameUser() {
        String key = DefaultIdenticon.objectKeyFor("user-123");

        assertTrue(DefaultIdenticon.isDefaultObjectKey(key));
        assertEquals(DefaultIdenticon.dataUrl(key), DefaultIdenticon.dataUrl(key));
        assertTrue(DefaultIdenticon.dataUrl(key).startsWith("data:image/svg+xml;base64,"));
    }

    @Test
    void usesDifferentArtworkForDifferentUsers() {
        assertNotEquals(
                DefaultIdenticon.dataUrl(DefaultIdenticon.objectKeyFor("user-123")),
                DefaultIdenticon.dataUrl(DefaultIdenticon.objectKeyFor("user-456"))
        );
    }
}
