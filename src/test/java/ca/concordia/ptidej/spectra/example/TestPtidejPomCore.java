package ca.concordia.ptidej.spectra.example;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import ca.concordia.ptidej.spectra.Profile.Constants;
import ca.concordia.ptidej.spectra.Profile.Launcher;
import org.junit.Test;
import org.noureddine.joularjx.result.CsvResultWriter;
import org.noureddine.joularjx.result.ResultWriter;


import static org.junit.Assert.assertTrue;

public class TestPtidejPomCore {
    @Test
    public void testOverload() throws Exception {
        ResultWriter writer = new CsvResultWriter();
        final Launcher launcher = new Launcher();
//
        String mavenDeps = new String(Files.readAllBytes(
                Paths.get("target/classpath.txt")));

        String testClasses = Constants.PROJECT_ROOT + "/target/test-classes";
        String mainClasses = Constants.PROJECT_ROOT + "/target/classes";

        String completeClasspath = mavenDeps
                + File.pathSeparator + testClasses
                + File.pathSeparator + mainClasses
                + File.pathSeparator + Constants.SPECTRA_ROOT + "/target/test-classes"
                + File.pathSeparator + Constants.SPECTRA_ROOT + "/target/classes"
                + File.pathSeparator + Constants.SPECTRA_ROOT + "/target/Spectra-with-dependencies.jar"
                + File.pathSeparator + Constants.PROJECT_ROOT + "/target/pom-core-1.0.0-tests.jar"
                + File.pathSeparator + Constants.PROJECT_ROOT + "/target/pom-core-1.0.0.jar"
                + File.pathSeparator + Constants.PROJECT_ROOT + "../CPL/target/cpl-core-1.0.0.jar"
                + File.pathSeparator + Constants.PROJECT_ROOT + "../PADL/target/padl-core-1.0.0.jar"
                + File.pathSeparator + Constants.PROJECT_ROOT + "../PADL/target/padl-core-1.0.0.jar"
                + File.pathSeparator + Constants.PROJECT_ROOT + "../PADL/target/padl-core-1.0.0.jar"
                + File.pathSeparator + Constants.PROJECT_ROOT + "../PADL/target/padl-core-1.0.0.jar"
                + File.pathSeparator + Constants.PROJECT_ROOT + "../PADL/target/padl-core-1.0.0.jar"
                + File.pathSeparator + Constants.PROJECT_ROOT + "../PADL/target/padl-core-1.0.0.jar"
                + File.pathSeparator + Constants.PROJECT_ROOT + "../PADL/target/padl-core-1.0.0.jar"
                + File.pathSeparator + Constants.PROJECT_ROOT + "../PADL Statements/target/padl-creator-classfile-1.0.0.jar";


        System.out.println("Complete Classpath:" + completeClasspath);

        long fileExists = launcher.launch(
                writer,
                completeClasspath,
                "org.junit.runner.JUnitCore",
                "pom.test.TestPOM");


        assertTrue("Excel files created", fileExists != 0);

    }


}
