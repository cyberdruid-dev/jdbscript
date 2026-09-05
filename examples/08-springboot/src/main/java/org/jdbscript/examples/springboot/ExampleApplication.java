package org.jdbscript.examples.springboot;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot application - it exists only so {@code @SpringBootTest} has a
 * configuration class to bootstrap. jdbscript has no idea this is Spring; it only ever sees the
 * {@link javax.sql.DataSource} bean Spring already wired up.
 */
@SpringBootApplication
public class ExampleApplication {
}
