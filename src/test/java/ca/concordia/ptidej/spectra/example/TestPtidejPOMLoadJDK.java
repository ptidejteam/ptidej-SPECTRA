package ca.concordia.ptidej.spectra.example;

import ca.concordia.ptidej.spectra.joularjx.Launcher;
import org.junit.Test;
import org.noureddine.joularjx.result.CsvResultWriter;
import org.noureddine.joularjx.result.ResultWriter;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertTrue;


public class TestPtidejPOMLoadJDK {
@Test
    public void testOverload() throws IOException {

        ResultWriter writer = new CsvResultWriter();
        final Launcher launcher = new Launcher();

        String junitPath = "~/.m2/repository/junit/junit/4.12/junit-4.12.jar";
        String hamcrestPath = "~/.m2/repository/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar";

        //Replace this with absolute path to your Ptidej POM target classes and jar
        // Mac Users shouldn't be able to use path with "~", so  (~/Downloads/...) won't work

        String ptidejClasspath = "../Ptidej/ptidej-Ptidej/POM/target/test-classes"
                + File.pathSeparator + "../Ptidej/ptidej-Ptidej/POM/target/pom-core-1.0" +
                ".0-jar-with-dependencies.jar"
                + File.pathSeparator + junitPath
                + File.pathSeparator + hamcrestPath;

        boolean result = launcher.launch(
                writer,
                ptidejClasspath, "org.junit.runner.JUnitCore",
                        "pom.test.classfile.general.TestLoadJDK10"
        );
    assertTrue("Launcher returned false: expected successful run", result);
       }
}
