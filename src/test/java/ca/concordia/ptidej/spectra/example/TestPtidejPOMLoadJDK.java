package ca.concordia.ptidej.spectra.example;

import ca.concordia.ptidej.spectra.Profile.Constants;
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


        String testClasses = Constants.PROJECT_ROOT + "/POM/target/test-classes";
        String mainClasses = Constants.PROJECT_ROOT + "/POM/target/classes";

        String completeClasspath = mavenDeps + File.pathSeparator + testClasses + File.pathSeparator + mainClasses + File.pathSeparator + Constants.SPECTRA_ROOT + "/SPECTRA/src/main/resources/junit-4.13.2.jar"
                + File.pathSeparator + Constants.SPECTRA_ROOT + "/src/main/resources/hamcrest-core-1.3.jar" + File.pathSeparator + Constants.SPECTRA_ROOT + "/target/test-classes:"+ Constants.SPECTRA_ROOT + "/SPECTRA/target/classes:" + Constants.SPECTRA_ROOT + "/target/Spectra-with-dependencies.jar"
                + File.pathSeparator + Constants.PROJECT_ROOT + "/POM/target/pom-core-1.0.0-tests.jar" + File.pathSeparator + Constants.PROJECT_ROOT + "/POM/target/pom-core-1.0.0.jar";

        System.out.println("Complete Classpath:" + completeClasspath);

        long fileExists = launcher.launch(
                writer,
                completeClasspath,
                "org.junit.runner.JUnitCore",
                "pom.test.classfile.general.TestJDKLoad10");


        assertTrue("Excel files created", fileExists != 0);

    }
}
