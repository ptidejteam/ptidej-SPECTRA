package ca.concordia.ptidej.spectra.example;

import ca.concordia.ptidej.spectra.joularjx.Launcher;
import org.junit.Test;
import org.noureddine.joularjx.result.CsvResultWriter;
import org.noureddine.joularjx.result.ResultWriter;

import java.io.File;
import java.io.IOException;

public class TestPtidejPOM {

    @Test
    public void testOverload() throws IOException {

        ResultWriter writer = new CsvResultWriter();
        final Launcher launcher = new Launcher();

        String classpath = "../POM/target/test-classes:../POM/target/pom-core-1.0.0-tests.jar"
                + File.pathSeparator + "../POM/target/pom-core-1.0.0-jar-with-dependencies.jar";

        launcher.launch(
                writer,
                classpath, "org.junit.runner.JUnitCore",
                        "pom.test.TestPOM"
        );

       }


}

