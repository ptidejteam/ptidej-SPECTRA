package ca.concordia.ptidej.spectra.joularjx;

import java.io.IOException;
import java.util.Map;

import com.sun.jdi.Bootstrap;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.connect.VMStartException;
import org.noureddine.joularjx.result.ResultWriter;

public class Launcher {
	public void launch(final ResultWriter aWriter, final String aClasspath, final String aFQN)
			throws IllegalConnectorArgumentsException, VMStartException, IOException {
		// Set the ResultWriter for the Agent
		Agent.setResultWriter(aWriter);

		// Get the default launching connector
		LaunchingConnector launchingConnector = Bootstrap.virtualMachineManager().defaultConnector();

		// Get the default arguments
		Map<String, Connector.Argument> env = launchingConnector.defaultArguments();

		// Check required keys (without classpath because it may not exist)
		if (!env.containsKey("main") || !env.containsKey("options")) {
			throw new IllegalStateException("Missing required launch arguments.");
		}

		// Set the main class to launch
		env.get("main").setValue(aFQN);

		// Get existing options or empty string
		String existingOptions = env.get("options").value();
		if (existingOptions == null) existingOptions = "";

		// Remove deprecated debug flags that cause JDK 21 VM start failure
		existingOptions = existingOptions.replaceAll("(-Xdebug|-Xrunjdwp:[^\\s]+)", "").trim();

		// Add javaagent and classpath inside options
		String agentPath = "/Users/mac/Documents/RA/joularjx/joularjx/target/joularjx-3.0.1.jar";
		String newOptions = existingOptions + " -javaagent:" + agentPath + " -cp " + aClasspath;

		env.get("options").setValue(newOptions.trim());

		System.out.println("Launching JVM with:");
		System.out.println("Main Class : " + aFQN);
		System.out.println("Classpath  : " + aClasspath);
		System.out.println("Agent Path : " + agentPath);
		System.out.println("Options    : " + newOptions.trim());

		// Launch the JVM
		VirtualMachine vm = launchingConnector.launch(env);

		System.out.println("JVM started with agent: " + agentPath);
	}
}
