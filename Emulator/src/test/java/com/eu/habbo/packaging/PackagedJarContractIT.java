package com.eu.habbo.packaging;

import com.eu.habbo.packaging.probe.PackagedJarProbe;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackagedJarContractIT {

    @Test
    void executableJarPreservesVersionAndFlywayServiceDiscovery() throws Exception {
        Path jar = packagedJar();
        try (JarFile archive = new JarFile(jar.toFile())) {
            assertEquals(
                    "com.eu.habbo.Emulator",
                    archive.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS));
        }

        Path java = Path.of(
                System.getProperty("java.home"),
                "bin",
                System.getProperty("os.name", "").startsWith("Windows") ? "java.exe" : "java");
        String classpath = String.join(
                File.pathSeparator,
                Path.of("target", "test-classes").toAbsolutePath().toString(),
                jar.toAbsolutePath().toString());
        Process process = new ProcessBuilder(
                java.toString(),
                "-cp",
                classpath,
                PackagedJarProbe.class.getName())
                .redirectErrorStream(true)
                .start();

        assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Packaged JAR probe timed out");
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), output);
        assertTrue(output.contains("Packaged JAR contract verified"), output);
    }

    private static Path packagedJar() throws Exception {
        try (var files = Files.list(Path.of("target"))) {
            List<Path> jars = files
                    .filter(path -> path.getFileName().toString().matches("Polaris-.+-jar-with-dependencies\\.jar"))
                    .toList();
            assertEquals(1, jars.size(), "Expected one packaged Polaris executable JAR");
            return jars.getFirst();
        }
    }
}
