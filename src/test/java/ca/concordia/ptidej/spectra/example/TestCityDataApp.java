package ca.concordia.ptidej.spectra.example;

import ca.concordia.ptidej.spectra.joularjx.Launcher;
import org.junit.Test;
import org.noureddine.joularjx.result.CsvResultWriter;
import org.noureddine.joularjx.result.ResultWriter;

import java.io.IOException;

public class TestCityDataApp {

    @Test
    public void testOverload() throws IOException {

        ResultWriter writer = new CsvResultWriter();
        final Launcher launcher = new Launcher();


        launcher.launch(writer, "/Users/mac/Documents/RA/TestProject/target/classes",
                "ca.concordia.encs.test.Main");

//            launcher.launch(writer, "/Users/mac/Documents/RA/jhotdraw/jhotdraw-gui/target/classes",
//                " org.jhotdraw.color.CIELCHabColorSpace");




    }


}

