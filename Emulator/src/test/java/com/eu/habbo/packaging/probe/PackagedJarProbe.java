package com.eu.habbo.packaging.probe;

import com.eu.habbo.Emulator;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.internal.database.DatabaseTypeRegister;

import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;

public final class PackagedJarProbe {

    private PackagedJarProbe() {
    }

    public static void main(String[] args) throws Exception {
        Properties project = new Properties();
        try (InputStream pom = PackagedJarProbe.class.getResourceAsStream(
                "/META-INF/maven/com.eu.habbo/Polaris/pom.properties")) {
            require(pom != null, "Packaged JAR is missing its Maven project metadata");
            project.load(pom);
        }

        Package emulatorPackage = Emulator.class.getPackage();
        require(
                Objects.equals(project.getProperty("version"), emulatorPackage.getImplementationVersion()),
                "Implementation-Version does not match the Maven project version");
        require(
                Objects.equals(project.getProperty("artifactId"), emulatorPackage.getImplementationTitle()),
                "Implementation-Title does not match the Maven artifact name");

        String databaseType = DatabaseTypeRegister
                .getDatabaseTypeForUrl("jdbc:mariadb://localhost/polaris", Flyway.configure())
                .getName();
        require("MariaDB".equals(databaseType), "Flyway MariaDB service provider was not discovered");

        System.out.println("Packaged JAR contract verified");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
