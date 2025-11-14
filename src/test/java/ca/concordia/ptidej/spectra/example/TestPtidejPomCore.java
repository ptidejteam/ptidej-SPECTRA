package ca.concordia.ptidej.spectra.example;

import ca.concordia.ptidej.spectra.joularjx.Launcher;
import org.junit.Test;
import org.noureddine.joularjx.result.CsvResultWriter;
import org.noureddine.joularjx.result.ResultWriter;

import java.io.File;
import java.io.IOException;

public class TestPtidejPomCore {

    @Test
    public void testOverload() throws IOException {

        ResultWriter writer = new CsvResultWriter();
        final Launcher launcher = new Launcher();

        String junitPath = "~/.m2/repository/junit/junit/4.13.2/junit-4.13.2.jar";
        String hamcrestPath = "~/.m2/repository/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar";

        String ptidejClasspath = "../POM/target/pom-core-1.0.0-tests.jar"
                + File.pathSeparator + "../POM/target/pom-core-1.0.0-jar-with-dependencies.jar";

        launcher.launch(
                writer,
                ptidejClasspath, "org.junit.runner.JUnitCore",
                        "pom.test.classfile.specific.TestAID"
        );

       }


}

