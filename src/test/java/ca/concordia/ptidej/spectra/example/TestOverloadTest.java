package ca.concordia.ptidej.spectra.example;

import java.io.IOException;

import ca.concordia.ptidej.spectra.Profile.Launcher;
import org.junit.Test;

import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.VMStartException;

import ca.concordia.ptidej.spectra.analysis.CsvResultWriter;

public class TestOverloadTest {
    @Test
    public void testOverload() throws IllegalConnectorArgumentsException,
            VMStartException, IOException {

        CsvResultWriter writer = new CsvResultWriter();
        final Launcher launcher = new Launcher();

        launcher.launch(writer, "/Users/mac/Documents/RA/SPECTRA/target/classes:/Users/mac/Documents/RA/SPECTRA/target/Spectra-with-dependencies.jar","ca.concordia.ptidej.spectra.example.OverloadTest");


    }

}