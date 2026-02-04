package ca.concordia.ptidej.spectra.example;

import ca.concordia.ptidej.spectra.Profile.Constants;
import ca.concordia.ptidej.spectra.Profile.Launcher;
import org.junit.Test;
import org.noureddine.joularjx.result.CsvResultWriter;
import org.noureddine.joularjx.result.ResultWriter;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class TestPtidejCPL {
    @Test
    public void testOverload() throws Exception {
        ResultWriter writer = new CsvResultWriter();
        final Launcher launcher = new Launcher();
//
        String mavenDeps = new String(Files.readAllBytes(
                Paths.get("target/classpath.txt")));


        String testClasses = Constants.PROJECT_ROOT + "/target/test-classes";
        String mainClasses = Constants.PROJECT_ROOT + "/target/classes";

//        String completeClasspath = mavenDeps
//                + File.pathSeparator + testClasses
//                + File.pathSeparator + mainClasses
//                + File.pathSeparator + "../../../SPECTRA/src/main/resources/junit-4.13.2.jar"
//                + File.pathSeparator + "../../../SPECTRA/src/main/resources/hamcrest-core-1.3.jar"
//                + File.pathSeparator + "../../../SPECTRA/target/test-classes" +
//                ":../../../SPECTRA/target/classes" +
//                ":../../../SPECTRA/target/Spectra-with-dependencies.jar"
//                + File.pathSeparator + "../CPL/target/cpl-core-1.0.0.jar";

        String completeClasspath = mavenDeps
                + File.pathSeparator + testClasses
                + File.pathSeparator + mainClasses
                + File.pathSeparator + Constants.SPECTRA_ROOT + "/target/Spectra-with-dependencies.jar"
                + File.pathSeparator + Constants.PROJECT_ROOT + "/target/classes/cfparse.jar"
                + File.pathSeparator + Constants.PROJECT_ROOT + "/target/cpl-core-1.0.0.jar";

        System.out.println("Complete Classpath:" + completeClasspath);

        long fileExists = launcher.launch(
                writer,
                completeClasspath,
                "org.junit.runner.JUnitCore",
                "cpl.test.TestCPL");


        assertTrue("Excel files created", fileExists != 0);

    }


}
