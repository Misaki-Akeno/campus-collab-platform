package com.campus.common.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnowflakeIdUtilTest {

    private static final String TEST_PROPERTY = "campus.snowflake.test-node-id";

    @AfterEach
    void clearProperty() {
        System.clearProperty(TEST_PROPERTY);
    }

    @Test
    void resolveNodeId_prefersExplicitProperty() {
        System.setProperty(TEST_PROPERTY, "17");

        assertEquals(17L, SnowflakeIdUtil.resolveNodeId(TEST_PROPERTY,
                "CAMPUS_SNOWFLAKE_TEST_UNUSED", "fallback"));
    }

    @Test
    void resolveNodeId_derivesValueWithinSnowflakeRange() {
        long value = SnowflakeIdUtil.resolveNodeId(TEST_PROPERTY,
                "CAMPUS_SNOWFLAKE_TEST_UNUSED", "node-a");

        assertTrue(value >= 0 && value <= 31);
    }

    @Test
    void resolveNodeId_rejectsOutOfRangeConfiguration() {
        System.setProperty(TEST_PROPERTY, "32");

        assertThrows(IllegalStateException.class, () -> SnowflakeIdUtil.resolveNodeId(
                TEST_PROPERTY, "CAMPUS_SNOWFLAKE_TEST_UNUSED", "fallback"));
    }
}
