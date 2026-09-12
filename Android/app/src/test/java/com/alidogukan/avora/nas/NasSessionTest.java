package com.alidogukan.avora.nas;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NasSessionTest {
    private static final NasSession.User USER =
            new NasSession.User("user_1", "owner@example.com", "Owner", "admin");

    @Test
    public void sessionIsActiveOnlyBeforeExpiry() {
        NasSession session = new NasSession("temporary-token", 200L, USER);

        assertTrue(session.isActiveAt(199L));
        assertFalse(session.isActiveAt(200L));
        assertFalse(session.isActiveAt(201L));
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyTokenIsRejected() {
        new NasSession(" ", 200L, USER);
    }
}
