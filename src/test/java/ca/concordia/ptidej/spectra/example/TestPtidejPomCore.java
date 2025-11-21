package ca.concordia.ptidej.spectra.example;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import ca.concordia.ptidej.spectra.Profile.Launcher;
import org.junit.Test;
import org.noureddine.joularjx.result.CsvResultWriter;
import org.noureddine.joularjx.result.ResultWriter;

public class TestPtidejPomCore {
        @Test
        public void testOverload() throws IOException {
                ResultWriter writer = new CsvResultWriter();
                final Launcher launcher = new Launcher();

                // Step 1: Read Maven dependencies
                String mavenDeps = new String(Files.readAllBytes(
                                Paths.get("target/classpath.txt")));

                // Step 2: Add your test classes directory and main classes directory
                // We use relative paths assuming the test is run from the project root
                // (SPECTRA)
                String testClasses = new File("target/test-classes").getCanonicalPath();
                String mainClasses = new File("target/classes").getCanonicalPath();

                // Step 3: Combine them ALL
                // The classpath.txt from maven-dependency-plugin uses the system path separator
                String completeClasspath = mavenDeps + File.pathSeparator + testClasses + File.pathSeparator + mainClasses;

                System.out.println("Complete Classpath:" + completeClasspath);

                // NOW it works! TestPOM will be found
                launcher.launch(
                                writer,
                                completeClasspath, // ← COMPLETE: Maven deps + test classes!
                                "org.junit.runner.JUnitCore",
                                "pom.test.TestPOM");
        }

}
