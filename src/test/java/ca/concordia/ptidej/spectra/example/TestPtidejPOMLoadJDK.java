package ca.concordia.ptidej.spectra.example;

import ca.concordia.ptidej.spectra.Profile.Launcher;
import org.junit.Test;
import org.noureddine.joularjx.result.CsvResultWriter;
import org.noureddine.joularjx.result.ResultWriter;

import java.io.File;
import java.io.IOException;
public class TestPtidejPOMLoadJDK {
@Test
    public void testOverload() throws IOException {

        ResultWriter writer = new CsvResultWriter();
        final Launcher launcher = new Launcher();

    String classpath = "/Users/mac/Documents/RA/SPECTRA/src/main/resources/junit-4.13.2.jar:../POM/target/test-classes:../POM/target/pom-core-1.0.0-tests.jar";

        launcher.launch(
                writer,
                classpath, "org.junit.runner.JUnitCore",
                        "pom.test.classfile.general.TestLoadJDK10"
        );

       }
}
