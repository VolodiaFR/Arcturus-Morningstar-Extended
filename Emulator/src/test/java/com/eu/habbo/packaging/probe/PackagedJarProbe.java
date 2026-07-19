package com.eu.habbo.packaging.probe;

import com.eu.habbo.Emulator;
import org.flywaydb.core.extensibility.Plugin;

import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;
import java.util.ServiceLoader;

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

        boolean mariaDbProviderDiscovered = ServiceLoader.load(Plugin.class).stream()
                .map(ServiceLoader.Provider::type)
                .map(Class::getName)
                .anyMatch("org.flywaydb.database.mysql.mariadb.MariaDBDatabaseType"::equals);
        require(mariaDbProviderDiscovered, "Flyway MariaDB service provider was not discovered");

        System.out.println("Packaged JAR contract verified");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
