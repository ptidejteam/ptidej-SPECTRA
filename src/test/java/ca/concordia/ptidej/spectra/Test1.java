package ca.concordia.ptidej.spectra;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.VMStartException;
import org.junit.*;
import org.noureddine.joularjx.result.ResultWriter;

import ca.concordia.ptidej.spectra.joularjx.Agent;
import ca.concordia.ptidej.spectra.joularjx.Launcher;

public class Test1 {
	@Test
	public void test1() throws IllegalConnectorArgumentsException, VMStartException, IOException {
		final Map<String, Double> methodsPowers = new HashMap<String, Double>();
		final ResultWriter writer = new ResultWriter() {
			@Override
			public void write(final String methodName, final double methodPower) throws IOException {
				methodsPowers.put(methodName, methodPower);
			}

			@Override
			public void setTarget(final String name, final boolean overwrite) throws IOException {
			}

			@Override
			public void closeTarget() throws IOException {
			}
		};

		// java -javaagent:../joularjx/target/joularjx-3.0.1.jar -cp target/classes ca.concordia.ptidej.spectra.OverloadTest
		// ...("ca.concordia.ptidej.spectra.example.OverloadTest")...;
		final Launcher launcher = new Launcher();
		launcher.launch(writer, "target/test-classes", "ca.concordia.ptidej.spectra.example.OverloadTest");



		// Assertion
		Assert.assertFalse("No methods were captured", methodsPowers.isEmpty());

		// Adjust method name as per your resolved format (e.g., fully qualified with parameters)
		String expectedMethod = "ca.concordia.ptidej.spectra.example.OverloadTest.add(int,int)";
		Assert.assertTrue("Expected method not found", methodsPowers.containsKey(expectedMethod));


		Assert.assertFalse(methodsPowers.isEmpty());
		Assert.assertEquals(0, methodsPowers.size());
		Assert.assertTrue(methodsPowers.containsKey("add")); // Goal: "add(int, int)"
		Assert.assertTrue(methodsPowers.get("add") > 25);
	}
}
