package ca.concordia.ptidej.spectra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.VMStartException;

import ca.concordia.ptidej.spectra.joularjx.Launcher;
import org.noureddine.joularjx.result.CsvResultWriter;
import org.noureddine.joularjx.result.ResultWriter;

public class TestOverloadMethods {
    @Test
    public void test1() throws IllegalConnectorArgumentsException, VMStartException, IOException {
        File path = new File("Output/Joularjx/");

        final Map<String, Double> methodsPowers = new HashMap<String, Double>();
        ResultWriter writer = new CsvResultWriter();
        final Launcher launcher = new Launcher();
        launcher.launch(writer, "target/test-classes", "ca.concordia.ptidej.spectra.example.OverloadTest");

    }
}
