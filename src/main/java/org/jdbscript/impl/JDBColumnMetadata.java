package org.jdbscript.impl;

public class JDBColumnMetadata {
    private final String name;

    public JDBColumnMetadata(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
