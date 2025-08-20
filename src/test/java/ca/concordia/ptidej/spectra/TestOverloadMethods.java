package ca.concordia.ptidej.spectra;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.noureddine.joularjx.result.CsvResultWriter;
import org.noureddine.joularjx.result.ResultWriter;

import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.VMStartException;

import ca.concordia.ptidej.spectra.joularjx.Launcher;

import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class TestOverloadMethods {
	@Test
	public void testOverload() throws IllegalConnectorArgumentsException,
			VMStartException, IOException {

		ResultWriter writer = new CsvResultWriter();
		final Launcher launcher = new Launcher();

//        String mainClass = getMainClassFromJar("/Users/mac/Documents/RA/Ptidej/tools4cities-middleware/Middleware/target/Middleware-0.0.1-SNAPSHOT.jar");
//        System.out.println("➡ Main class detected: " + mainClass);

//		// Class File
      launcher.launch(writer, "target/classes","ca.concordia.ptidej.spectra.example" +
                ".OverloadTest");
//        launcher.launch(writer, "/Users/mac/Documents/RA/TestProject/target/classes",
//                "com.concordia.encs.citydata.Main");

//		Assert.assertTrue("Data found in output", resultsUsingClassFile.stream()
//				.anyMatch(line -> line.contains("ca.concordia")));
//		Assert.assertFalse("Data found", resultsUsingClassFile.isEmpty());





        // Call Spectra's launch here
        //launcher.launch(writer, jarPath, mainClass);

	}
    private static String getMainClassFromJar(String jarPath) throws IOException {
        try (JarFile jarFile = new JarFile(new File(jarPath))) {
            Manifest manifest = jarFile.getManifest();
            if (manifest == null) return null;

            Attributes attrs = manifest.getMainAttributes();

            // Spring Boot jars have "Start-Class"
            String startClass = attrs.getValue("Start-Class");
            if (startClass != null && !startClass.isEmpty()) {
                return startClass;
            }

            // Normal jars have "Main-Class"
            String mainClass = attrs.getValue("Main-Class");
            if (mainClass != null && !mainClass.isEmpty()) {
                return mainClass;
            }

            return null; // no entry found
        }
    }

}

