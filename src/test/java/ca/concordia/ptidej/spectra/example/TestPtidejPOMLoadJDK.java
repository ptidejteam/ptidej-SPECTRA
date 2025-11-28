package ca.concordia.ptidej.spectra.example;

import ca.concordia.ptidej.spectra.Profile.Launcher;
import org.junit.Test;
import org.noureddine.joularjx.result.CsvResultWriter;
import org.noureddine.joularjx.result.ResultWriter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static java.nio.file.Files.readAllBytes;
import static org.junit.Assert.assertTrue;

public class TestPtidejPOMLoadJDK  {
    @Test
public void testOverload() throws IOException {
        ResultWriter writer = new CsvResultWriter();
        final Launcher launcher = new Launcher();
        //
        String mavenDeps = new String(readAllBytes(Paths.get("target/classpath.txt")));


        String testClasses = "../POM/target/test-classes";
        String mainClasses = "../POM/target/classes";

        String completeClasspath = mavenDeps + File.pathSeparator + testClasses + File.pathSeparator + mainClasses + File.pathSeparator + "../../../SPECTRA/src/main/resources/junit-4.13.2.jar"
                + File.pathSeparator + "../../../SPECTRA/src/main/resources/hamcrest-core-1.3.jar" + File.pathSeparator + "../../../SPECTRA/target/test-classes:../../../SPECTRA/target/classes:../../../SPECTRA/target/Spectra-with-dependencies.jar"
                + File.pathSeparator + "../POM/target/pom-core-1.0.0-tests.jar" + File.pathSeparator + "../POM/target/pom-core-1.0.0.jar";

        System.out.println("Complete Classpath:" + completeClasspath);

        long fileExists = launcher.launch(
                writer,
                completeClasspath,
                "org.junit.runner.JUnitCore",
                "pom.test.classfile.general.TestJDKLoad10");


        assertTrue("Excel files created", fileExists != 0);

    }
}
