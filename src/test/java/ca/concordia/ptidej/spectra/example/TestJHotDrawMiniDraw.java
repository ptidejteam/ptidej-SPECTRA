package ca.concordia.ptidej.spectra.example;

import ca.concordia.ptidej.spectra.joularjx.Launcher;
import org.junit.Test;
import org.noureddine.joularjx.result.CsvResultWriter;
import org.noureddine.joularjx.result.ResultWriter;

import java.io.IOException;

public class TestJHotDrawMiniDraw {

	@Test
	public void testJHotDrawMiniDraw() throws IOException {

		ResultWriter writer = new CsvResultWriter();
		final Launcher launcher = new Launcher();
        launcher.launch(writer,
                "../jhotdraw/jhotdraw-samples/jhotdraw-samples-mini/target/classes:" +
                        "../jhotdraw/jhotdraw-samples/jhotdraw-samples-misc/target" +
                        "/classes:"+
                        "../jhotdraw/jhotdraw-api/target/classes:" +
                        "../jhotdraw/jhotdraw-io/target/classes:" +
                        "../jhotdraw/jhotdraw-actions/target/classes:" +
                        "../jhotdraw/jhotdraw-datatransfer/target/classes:" +
                        "../jhotdraw/jhotdraw-xml/target/classes:" +
                        "../jhotdraw/jhotdraw-core/target/classes:" +
                        "../jhotdraw/jhotdraw-gui/target/classes:" +
                        "../jhotdraw/jhotdraw-utils/target/classes:",
                "org.jhotdraw.samples.mini.SVGDrawingPanelSample");

    }
}
