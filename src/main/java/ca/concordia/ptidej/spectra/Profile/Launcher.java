package ca.concordia.ptidej.spectra.Profile;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.jprofiler.api.controller.Controller;
import com.sun.jdi.event.*;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.MethodExitRequest;
import com.sun.jdi.request.VMDeathRequest;
import org.noureddine.joularjx.result.ResultWriter;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import com.sun.jdi.Bootstrap;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector.Argument;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.event.VMDeathEvent;

import ca.concordia.ptidej.spectra.analysis.CSVMerger;

public class Launcher {

	private static final int JPROFILER_PORT = 8849;

	public long launch(final ResultWriter aWriter, final String aClasspath, final String aFQN,
			final String... programArgs) throws IOException {

		// 1. Launch JProfiler
		final long jprofilerPid = this.launchJProfilerPhase(aClasspath, aFQN, programArgs);

		if (jprofilerPid > 0) {
			System.out.println("Finished Executing JProfiler with PID: " + jprofilerPid);

		} else {
			throw new RuntimeException("Failed to launch JProfiler.");
		}

		// 2. Launch JVM with Joularjx and Spectra agent
		System.out.println("Launching Joularjx with Spectra agent...");
		long joularProcessId = launchJoularjxPhase(aClasspath, aFQN, programArgs);

		if (joularProcessId > 0) {
			System.out.println("Finished Executing Joular with PID: " + joularProcessId);
			System.out.println("Launched all JVMs and tools successfully.");
		} else {
			throw new RuntimeException("Failed to launch Joular.");
		}

		// 3. Merge CSV results
		if (programArgs.length > 0 || joularProcessId > 0) {
			String mergeArg = (programArgs != null && programArgs.length > 0) ? Arrays.toString(programArgs) : aFQN;
			CSVMerger.runCSVMerger(mergeArg);
		}
		return joularProcessId;

	}

	private long launchJProfilerPhase(final String aClasspath, final String aFQN, final String... programArgs)
			throws IOException {
		System.out.println("=== PHASE 1: JProfiler Profiling ===");

		// Step 1: Launch JVM in suspended mode
		final String javaPath = System.getProperty("java.home");
		final String jprofilerAgent = Constants.JPROFILER_AGENT;

		System.out.println("Step 1: Launching JVM in suspended mode with JProfiler...");
		final Process jvmProcess = launchJVMSuspended(javaPath, jprofilerAgent, aClasspath, aFQN, programArgs);

		System.out.println("Waiting for profiled JVM to terminate naturally...");
		int exitCode = 0;
		try {
			exitCode = jvmProcess.waitFor();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		System.out.println("Profiled JVM exited with code: " + exitCode);

		return jvmProcess.pid();
	}

	// Step 1.1: Launch JVM in suspended mode with JProfiler agent
	private Process launchJVMSuspended(final String javaPath, final String jprofilerAgent, final String classpath,
			final String mainClass, final String... programArgs) throws IOException {
		final List<String> command = new ArrayList<>();
		command.add(javaPath + "/bin" + "/java");

		// JProfiler agent with suspended mode and specific port
		command.add("-agentpath:" + jprofilerAgent + "=port=" + JPROFILER_PORT + ",nowait" + ",config="
				+ Constants.SPECTRA_ROOT + "/src/main/resources/jprofiler_config.xml" + ",id=110");
		command.add("-javaagent:" + Constants.MY_JPROFILER_AGENT_PATH);
		command.add("-cp");
		command.add(classpath);
		command.add("-Dspectra.realMain=" + mainClass);
		command.add("-Djdk.attach.allowAttachSelf=true");
		command.add("-agentlib:jdwp=transport=dt_socket,server=y,address=5005,suspend=n");
		command.add("ca.concordia.ptidej.spectra.ChildJVM.SpectraMain");

		if (programArgs != null) {
			for (String arg : programArgs) {
				command.add(arg);
			}
		}

		System.out.println("Command: " + command);
		final ProcessBuilder processBuilder = new ProcessBuilder(command);
		processBuilder.redirectErrorStream(true);

		// // Change Current Working Directory
        File newCurrentWorkingDirectory = new File(Constants.PROJECT_ROOT);

        processBuilder.directory(newCurrentWorkingDirectory);
		// Start the process
		final Process process = processBuilder.start();


		System.out.println("Launched JVM in suspended mode with PID: " + process.pid() + " at directory:"
				+ newCurrentWorkingDirectory.getPath());

		// Consume output in background thread
		consumeProcessOutput(process);

		return process;
	}

	// Phase 2: Joularjx profiling (similar pattern)

	private long launchJoularjxPhase(final String aClasspath, final String aFQN, final String... programArgs)
			throws IOException {
		System.out.println("\n=== PHASE 2: Joularjx Profiling ===");

		final String javaPath = System.getProperty("java.home");
		final String spectraAgentPath = Constants.MY_AGENT_PATH;

		final List<String> command = new ArrayList<>();
		command.add("sudo");
		command.add("-S");
		command.add(javaPath + File.separator + "bin" + File.separator + "java");
		command.add("-javaagent:" + spectraAgentPath);
		command.add("-Djoularjx.config=" + Constants.SPECTRA_ROOT + "/src/test/resources/config.properties");
		command.add("-cp");
		// Include both main classes and test classes for JUnit execution
		String testClasses = Constants.SPECTRA_ROOT + "/target/test-classes";
		command.add(aClasspath + File.pathSeparator + testClasses);
		command.add("-Dspectra.realMain=" + aFQN);
		command.add("ca.concordia.ptidej.spectra.ChildJVM.SpectraMain");

		if (programArgs != null) {
			for (String arg : programArgs) {
				command.add(arg);
			}
		}

		System.out.println("Command: " + command);
		final ProcessBuilder processBuilder = new ProcessBuilder(command);
		processBuilder.redirectErrorStream(true);

		// Change Current Working Directory
        File newCurrentWorkingDirectory = new File(Constants.PROJECT_ROOT);

        processBuilder.directory(newCurrentWorkingDirectory);
		final Process process = processBuilder.start();

		// Enter sudo password
		enterPassword(process);

		// Consume output in background
		consumeProcessOutput(process);

		try {
			int exitCode = process.waitFor();
			System.out.println("Joularjx process exited with code: " + exitCode);
		} catch (InterruptedException e) {
			System.err.println("Joularjx process wait was interrupted");
			Thread.currentThread().interrupt();
			throw new RuntimeException(e);
		}

		return process.pid();
	}

	private void consumeProcessOutput(final Process process) {
		// Consume output stream
		new Thread(() -> {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				String line;
				while ((line = reader.readLine()) != null) {
					System.out.println("[PROCESS OUT] " + line);
				}
			} catch (IOException e) {
				// Stream closed
			}
		}).start();

		// Consume error stream
		new Thread(() -> {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
				String line;
				while ((line = reader.readLine()) != null) {
					System.err.println("[PROCESS ERR] " + line);
				}
			} catch (IOException e) {
				// Stream closed
			}
		}).start();
	}

	// Enter sudo password for privileged operations
	private void enterPassword(final Process process) throws IOException {
		try (OutputStream os = process.getOutputStream()) {
			// Replace with your actual password handling mechanism
			os.write((Constants.SUDO_PWD + '\n').getBytes());
			os.flush();
		}
	}
}
