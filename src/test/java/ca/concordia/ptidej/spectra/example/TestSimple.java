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

public class TestSimple {

    @Test
    public void simpleTest() throws Exception {

        System.out.println("[BAD] not supposed to call this");
        ResultWriter writer = new CsvResultWriter();
        final Launcher launcher = new Launcher();

        String mavenDeps = new String(Files.readAllBytes(
                Paths.get("target/classpath.txt")));


        String testClasses = Constants.SPECTRA_ROOT + "/target/test-classes";
        String mainClasses = Constants.SPECTRA_ROOT + "/target/classes";

        String completeClasspath = mavenDeps
                + File.pathSeparator + testClasses
                + File.pathSeparator + mainClasses
                + File.pathSeparator + Constants.SPECTRA_ROOT + "/target/Spectra-with-dependencies.jar";

        System.out.println("Complete Classpath:" + completeClasspath);

        long fileExists = launcher.launch(
                writer,
                completeClasspath,
                "org.junit.runner.JUnitCore",
                "ca.concordia.ptidej.spectra.example.TestSimpleTarget");
    }
}
