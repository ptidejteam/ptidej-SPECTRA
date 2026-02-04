package ca.concordia.ptidej.spectra.example;

import ca.concordia.ptidej.spectra.Profile.Constants;
import ca.concordia.ptidej.spectra.Profile.Launcher;
import org.junit.Test;
import org.noureddine.joularjx.result.CsvResultWriter;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestOverloadTest {
    @Test
    public void testOverload() throws Exception {

        CsvResultWriter writer = new CsvResultWriter();
        final Launcher launcher = new Launcher();

        long fileExists = launcher.launch(writer,
                Constants.PROJECT_ROOT + "/target/test-classes:"+ Constants.PROJECT_ROOT + "/target/classes:" +Constants.SPECTRA_ROOT + "/target/Spectra-with-dependencies.jar",
                "ca.concordia.ptidej.spectra.example.OverloadTest");

        String outputDir = Constants.SPECTRA_ROOT + "/Output/JProfiler/snapshot.jps";

        assertFalse("Snapshot file created", Files.exists(Paths.get(outputDir)));

        assertTrue("Excel files created", fileExists != 0);

    }

}