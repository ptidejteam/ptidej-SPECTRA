package ca.concordia.ptidej.spectra.example;

import java.io.IOException;

import org.junit.Test;
import org.noureddine.joularjx.result.CsvResultWriter;
import org.noureddine.joularjx.result.ResultWriter;

import ca.concordia.ptidej.spectra.joularjx.Launcher;

public class TestJHotDrawAnimationSample {

	@Test
	public void testJHotDraw() throws IOException {

		ResultWriter writer = new CsvResultWriter();
		final Launcher launcher = new Launcher();

        launcher.launch(writer,
                "../jhotdraw/jhotdraw-samples/jhotdraw-samples-mini/target/classes:" +
                        "../jhotdraw/jhotdraw-api/target/classes:"
                        + "../jhotdraw/jhotdraw-core/target/classes:"+"../jhotdraw/jhotdraw-gui/target/classes"
                +"../jhotdraw/jhotdraw-utils/target/classes:",
                "org.jhotdraw.samples.mini.AnimationSample");



    }
}
