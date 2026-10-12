package com.example.dhapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class Log4jVfsProtocolTest {

    @AfterEach
    void restore() {
        System.clearProperty(Log4jVfsProtocol.PROPERTY);
    }

    @Test
    void addsVfsWhenUnset() {
        System.clearProperty(Log4jVfsProtocol.PROPERTY);
        Log4jVfsProtocol.allow();
        assertTrue(System.getProperty(Log4jVfsProtocol.PROPERTY).contains("vfs"));
    }

    @Test
    void appendsVfsWithoutDroppingAnExistingList() {
        System.setProperty(Log4jVfsProtocol.PROPERTY, "file,https");
        Log4jVfsProtocol.allow();
        String value = System.getProperty(Log4jVfsProtocol.PROPERTY);
        assertTrue(value.contains("file"));
        assertTrue(value.contains("https"));
        assertTrue(value.contains("vfs"));
    }

    @Test
    void leavesAListThatAlreadyAllowsVfs() {
        System.setProperty(Log4jVfsProtocol.PROPERTY, "file,vfs");
        Log4jVfsProtocol.allow();
        String value = System.getProperty(Log4jVfsProtocol.PROPERTY);
        assertEquals("file,vfs", value);
    }
}
