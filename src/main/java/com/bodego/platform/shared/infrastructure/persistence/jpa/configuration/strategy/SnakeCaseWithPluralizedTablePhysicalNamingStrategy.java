package com.bodego.platform.shared.infrastructure.persistence.jpa.configuration.strategy;

import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;

import java.util.Locale;

import static io.github.encryptorcode.pluralize.Pluralize.pluralize;

public class SnakeCaseWithPluralizedTablePhysicalNamingStrategy
        implements PhysicalNamingStrategy {

    @Override
    public Identifier toPhysicalCatalogName(
            Identifier name, JdbcEnvironment context) {
        return toSnakeCase(name);
    }

    @Override
    public Identifier toPhysicalSchemaName(
            Identifier name, JdbcEnvironment context) {
        return toSnakeCase(name);
    }

    @Override
    public Identifier toPhysicalTableName(
            Identifier name, JdbcEnvironment context) {
        if (name == null) return null;
        return toSnakeCase(
                Identifier.toIdentifier(
                        pluralize(name.getText()),
                        name.isQuoted()
                )
        );
    }

    @Override
    public Identifier toPhysicalSequenceName(
            Identifier name, JdbcEnvironment context) {
        return toSnakeCase(name);
    }

    @Override
    public Identifier toPhysicalColumnName(
            Identifier name, JdbcEnvironment context) {
        return toSnakeCase(name);
    }

    private Identifier toSnakeCase(Identifier name) {
        if (name == null) return null;

        String snakeCase = name.getText()
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toLowerCase(Locale.ROOT);

        return Identifier.toIdentifier(
                snakeCase, name.isQuoted()
        );
    }
}
