package ca.concordia.ptidej.spectra.example;

import java.io.File;
import java.io.IOException;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.junit.Test;

import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.VMStartException;

import ca.concordia.ptidej.spectra.analysis.CsvResultWriter;
import ca.concordia.ptidej.spectra.joularjx.Launcher;

public class TestOverloadMethods {
	@Test
	public void testOverload() throws IllegalConnectorArgumentsException,
			VMStartException, IOException {

		CsvResultWriter writer = new CsvResultWriter();
		final Launcher launcher = new Launcher();

		      launcher.launch(writer, "target/classes","ca.concordia.ptidej.spectra.example" +
		                ".OverloadTest");





        // No fixed output path as each time new file is created
		//        String outputPath = writer.getOutputPath();
		//        File outputFile = new File(outputPath);
		//
		//        assertTrue(outputFile.exists(), "Output file should exist");
		//        assertTrue(outputFile.length() > 0, "Output file should not be empty");

	}

}
