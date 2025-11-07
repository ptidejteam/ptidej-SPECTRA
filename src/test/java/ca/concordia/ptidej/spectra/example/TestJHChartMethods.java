package ca.concordia.ptidej.spectra.example;

import java.io.IOException;

import org.junit.Test;
import org.noureddine.joularjx.result.CsvResultWriter;
import org.noureddine.joularjx.result.ResultWriter;

import ca.concordia.ptidej.spectra.joularjx.Launcher;

public class TestJHChartMethods {
	@Test
	public void testChart() throws IOException {

		ResultWriter writer = new CsvResultWriter();
		final Launcher launcher = new Launcher();

        launcher.launch(writer,
                "../XChart/xchart/target/classes:../XChart/xchart-demo/target/classes:",
                "org.knowm.xchart.demo.XChartDemo");


    }

}
