package dev.deepcore.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DeepCoreLogLevelTest {

    @Test
    void includes_respectsSeverityOrdering() {
        assertTrue(DeepCoreLogLevel.INFO.includes(DeepCoreLogLevel.WARN));
        assertTrue(DeepCoreLogLevel.DEBUG.includes(DeepCoreLogLevel.ERROR));
        assertFalse(DeepCoreLogLevel.ERROR.includes(DeepCoreLogLevel.INFO));
    }

    @Test
    void fromString_handlesNullBlankInvalidAndCase() {
        assertEquals(DeepCoreLogLevel.WARN, DeepCoreLogLevel.fromString(null, DeepCoreLogLevel.WARN));
        assertEquals(DeepCoreLogLevel.INFO, DeepCoreLogLevel.fromString("   ", DeepCoreLogLevel.INFO));
        assertEquals(DeepCoreLogLevel.DEBUG, DeepCoreLogLevel.fromString("debug", DeepCoreLogLevel.ERROR));
        assertEquals(DeepCoreLogLevel.ERROR, DeepCoreLogLevel.fromString("not-level", DeepCoreLogLevel.ERROR));
    }
}
