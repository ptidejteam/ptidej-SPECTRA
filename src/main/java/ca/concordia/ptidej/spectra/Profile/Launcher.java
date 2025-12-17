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
	private static final int JMX_PORT = 8850; // JProfiler MBean/JMX port (Must be different from JPROFILER_PORT)
	private static final int JDWP_PORT = 5005;
	private static final int PORT_CHECK_TIMEOUT_MS = 30000; // 30 seconds
	private static final int PORT_CHECK_INTERVAL_MS = 500; // Check every 500ms
	private static final String JPROFILER_OUTPUT_DIR = "Output/JProfiler/";
	private static final String SNAPSHOT_FILE = "snapshot.jps";

	private final AtomicBoolean cleanupRun = new AtomicBoolean(false);

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

		final long jvmPid = jvmProcess.pid();
		System.out.println("JVM Process ID: " + jvmPid);

		System.out.println("Step 2: Checking if JProfiler port is available...");
		// Step 2: Wait for JProfiler port to be available
		boolean portAvailable = waitForPortAvailable(JPROFILER_PORT, PORT_CHECK_TIMEOUT_MS);
		if (!portAvailable) {
			jvmProcess.destroyForcibly();
			throw new RuntimeException("JProfiler port " + JPROFILER_PORT + " did not become available within "
					+ PORT_CHECK_TIMEOUT_MS + "ms");
		}
		System.out.println("JProfiler port " + JPROFILER_PORT + " is available");

		System.out.println("Step 3: Attaching JPController ");
		// Step 3: Attach via JDI and listen for child VM termination, do not dispose vm
		// so events are delivered
		VirtualMachine vm = null;
		// Wait for JDWP port to be ready before attaching debugger
		boolean jdwpReady = waitForPortAvailable(JDWP_PORT, PORT_CHECK_TIMEOUT_MS);
		if (!jdwpReady) {
			jvmProcess.destroyForcibly();
			throw new RuntimeException("JDWP port " + JDWP_PORT + " not available");
		}
		try {
			vm = attachDebugger("localhost", JDWP_PORT);
			System.out.println("Attached and listening for VMDeath events");
		} catch (Exception e) {
			jvmProcess.destroyForcibly();
			throw new RuntimeException("Failed to attach JPController: " + e.getMessage(), e);
		}
		System.out.println("Step 4: Now start profiling *before* letting JVM run...");

		System.out.println("Step 5: Attaching Shutdown Listener for profiling cleanup...");

		// Resume target VM now that recordings are configured
		System.out.println("Step 6: Resuming target VM now...");
		vm.resume();

		// Wait for natural termination; JDI event thread will handle cleanup on
		// VMDeath.
		System.out.println("Waiting for profiled JVM to terminate naturally...");
		try {
			Thread.sleep(10000);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

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
				+ Constants.PROJECT_ROOT + "/src/main/resources/jprofiler_config.xml" + ",session=spectra filter");

		command.add("-cp");
		command.add(classpath);
		command.add("-Dspectra.realMain=" + mainClass);
		command.add("-Djdk.attach.allowAttachSelf=true");
		command.add("-agentlib:jdwp=transport=dt_socket,server=y,address=5005,suspend=y");
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
		File newCurrentWorkingDirectory = new File("../Ptidej/ptidej-Ptidej/POM/");
		processBuilder.directory(newCurrentWorkingDirectory);
		// Start the process
		final Process process = processBuilder.start();

		// Provide the sudo password
		enterPassword(process);
		System.out.println("Launched JVM in suspended mode with PID: " + process.pid() + " at directory:"
				+ newCurrentWorkingDirectory.getPath());

		// Consume output in background thread
		consumeProcessOutput(process);

		return process;
	}

	// Step 2: Wait for JProfiler port to become available
	private boolean waitForPortAvailable(final int port, final long timeoutMs) {
		final long startTime = System.currentTimeMillis();

		while (System.currentTimeMillis() - startTime < timeoutMs) {
			try (Socket socket = new Socket("localhost", port)) {
				System.out.println("Port " + port + " is now available");
				return true;
			} catch (IOException e) {
				// Port not available yet
				try {
					Thread.sleep(PORT_CHECK_INTERVAL_MS);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					return false;
				}
			}
		}

		System.err.println("Timeout waiting for port " + port + " to become available");
		return false;
	}

	private void registerShutdownHook(final Process jvmProcess) {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			System.out.println("=== SHUTDOWN HOOK: Profiling Cleanup ===");

			try {
				// Step 6: Stop all recordings
				System.out.println("Step 6: Stopping profiling recordings...");
				stopProfiling(jvmProcess.pid());

				// Step 7: Save snapshot
				System.out.println("Step 7: Saving profiling snapshot...");
				saveProfilerSnapshot();

				// Step 8: Export snapshot to CSVs
				System.out.println("Step 8: Exporting snapshot to CSVs...");
				exportProfilerSnapshot();

				System.out.println("=== Profiling cleanup completed ===");
			} catch (Exception e) {
				System.err.println("Error during profiling cleanup: " + e.getMessage());
				e.printStackTrace();
			}

			// Final cleanup
			if (jvmProcess.isAlive()) {
				System.out.println("Terminating JVM process...");
				jvmProcess.destroyForcibly();
			}
		}));
	}

	// Programmatically attach debugger (JDWP) and resume the suspended JVM using
	// JDI
	private VirtualMachine attachDebugger(final String host, final int port)
			throws IOException, IllegalConnectorArgumentsException {
		// Find socket attaching connector
		AttachingConnector socketConnector = null;
		for (AttachingConnector ac : Bootstrap.virtualMachineManager().attachingConnectors()) {
			if ("com.sun.jdi.SocketAttach".equals(ac.name()) || "dt_socket".equals(ac.transport().name())
					|| ac.name().contains("socket")) {
				socketConnector = ac;
				break;
			}
		}
		if (socketConnector == null) {
			throw new IOException("No socket attaching connector found");
		}

		// Set arguments: host & port
		final java.util.Map<String, Argument> args = socketConnector.defaultArguments();
		if (args.containsKey("hostname")) {
			args.get("hostname").setValue(host);
		} else if (args.containsKey("host")) {
			args.get("host").setValue(host);
		}
		args.get("port").setValue(String.valueOf(port));

		System.out.println(
				"Attempting to attach to JDWP at " + host + ":" + port + " using connector: " + socketConnector.name());
		final VirtualMachine vm;
		try {
			vm = socketConnector.attach(args);
		} catch (IOException e) {
			throw new IOException("Failed to attach to target VM on " + host + ":" + port + " - " + e.getMessage(), e);
		}

		return vm;
	}

	// Issue start Recording commands to JProfiler agent
	private void startProfilingRecordingsByAPI(long jvmPid) {
		try {
			System.out.println("Starting profiling recordings via jpcontroller CLI...");
			executeJpcontrollerCommand("startCPURecording", "true", jvmPid);
			executeJpcontrollerCommand("startAllocRecording", "true", jvmPid);
			executeJpcontrollerCommand("startMonitorRecording", "", jvmPid);
			executeJpcontrollerCommand("startThreadProfiling", "", jvmPid); // Optional,
			System.out.println("Profiling recordings started (CLI)");
		} catch (Exception e) {
			throw new RuntimeException("Failed to start profiling recordings via CLI: " + e.getMessage(), e);
		}
	}

	private void attachShutdownListener(final VirtualMachine vm, final String mainClass, long jvmPid) {
		Thread eventThread = new Thread(() -> {
			try {
				EventRequestManager erm = vm.eventRequestManager();

				// Fire on main() exit
				MethodExitRequest mer = erm.createMethodExitRequest();
				mer.addClassFilter(mainClass);
				mer.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD); // suspend only main thread
				mer.enable();

				// Fallback: VMDeath
				VMDeathRequest vdr = erm.createVMDeathRequest();
				vdr.setSuspendPolicy(EventRequest.SUSPEND_NONE);
				vdr.enable();

				EventQueue queue = vm.eventQueue();

				boolean done = false;
				while (!done) {
					EventSet eventSet = queue.remove(); // blocking
					for (Event event : eventSet) {

						if (event instanceof MethodExitEvent mee) {
							if ("main".equals(mee.method().name())
									&& mainClass.equals(mee.method().declaringType().name())) {

								System.out.println("main() exited — PAUSING JVM for snapshot...");

								runProfilerCleanupAPI(jvmPid);

								done = true;
							}
						}

						if (event instanceof VMDeathEvent || event instanceof VMDisconnectEvent) {
							System.out.println("VMDeath fallback");
							runProfilerCleanupAPI(jvmPid);
							done = true;
						}
					}

					// Resume suspended threads (if main was suspended)
					eventSet.resume();
				}

			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				try {
					vm.dispose();
				} catch (Throwable ignored) {
				}
			}
		});

		eventThread.setDaemon(true);
		eventThread.start();
	}

	private void runProfilerCleanupAPI(long jvmPid) {
		try {
			System.out.println("Stopping profiling via jpcontroller CLI...");

			String outputDir = Constants.PROJECT_ROOT + "/" + JPROFILER_OUTPUT_DIR;
			Files.createDirectories(Paths.get(outputDir));
			String snapshotPath = Paths.get(outputDir, SNAPSHOT_FILE).toString();

			System.out.println("Stop recordings...");
			executeJpcontrollerCommand("stopCPURecording", "", jvmPid);
			executeJpcontrollerCommand("stopAllocRecording", "", jvmPid);
			executeJpcontrollerCommand("stopMonitorRecording", "", jvmPid);

			System.out.println("Saving snapshot to: " + snapshotPath);
			executeJpcontrollerCommand("saveSnapshot", snapshotPath, jvmPid);

			// Verify snapshot was created
			File snapshotFile = new File(snapshotPath);
			if (snapshotFile.exists()) {
				System.out.println("Snapshot file created successfully: " + snapshotPath + " ( " + snapshotFile.length()
						+ " bytes)");
			} else {
				System.err.println("WARNING: Snapshot file was NOT created: " + snapshotPath);
			}

			int exitCode = exportProfilerSnapshot();

			if (exitCode != 0) {
				System.err.println("Exporting profiler snapshot failed with exit code: " + exitCode);
			}

			System.out.println("Profiling cleanup/export completed.");
		} catch (Exception e) {
			System.err.println("Profiler cleanup failed: " + e.getMessage());
			e.printStackTrace();
		}
	}

	// Execute JPController command via CLI using explicit JMX port
	public static void executeJpcontrollerCommand(final String command, final String parameter, long jvmPid)
			throws Exception {
		try {
			final List<String> cmdList = new ArrayList<>();
			cmdList.add(Constants.JPCONTROLLER_PATH);
			cmdList.add("-n");
			cmdList.add(String.valueOf(jvmPid));
			// cmdList.add("localhost:" + JPROFILER_PORT);
			cmdList.add(command);
			if (parameter != null && !parameter.isEmpty()) {
				cmdList.add(parameter);
			}

			System.out.println("Executing: " + cmdList);
			ProcessBuilder pb = new ProcessBuilder(cmdList);
			pb.redirectErrorStream(true);
			Process process = pb.start();

			StringBuilder out = new StringBuilder();
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				String line;
				while ((line = reader.readLine()) != null) {
					out.append(line).append(System.lineSeparator());
					System.out.println("[jpcontroller] " + line);
				}
			}
			int exitCode = process.waitFor();
			if (exitCode != 0) {
				System.err.println("JPController command '" + command + "' returned exit code: " + exitCode);

			}

		} catch (Exception e) {
			System.err.println("Error executing JPController command: " + command);
			throw e;
		}
	}

	// * Step 6: Stop all profiling recordings via JPController

	private void stopProfiling(long jvmPid) throws Exception {
		System.out.println("Stopping all profiling recordings...");
		String outputDir = Constants.PROJECT_ROOT + "/" + JPROFILER_OUTPUT_DIR;
		String snapshotPath = outputDir + SNAPSHOT_FILE;

		try {
			executeJpcontrollerCommand("stopCPURecording", "", jvmPid);
			executeJpcontrollerCommand("stopAllocRecording", "", jvmPid);
			executeJpcontrollerCommand("stopMonitorRecording", "", jvmPid);
		} catch (Exception e) {
			System.err.println("Error stopping profiling: " + e.getMessage());
			throw e;
		}
		// Ensure output directory exists before creating a temp file inside it
		Path outputDirPath = Paths.get(outputDir);
		Files.createDirectories(outputDirPath);

		Path cmdFile = Files.createTempFile(outputDirPath, "jpcommands", ".txt");

		String commands = "stopCPURecording\n" + "stopAllocRecording\n" + "stopMonitorRecording\n" + "saveSnapshot "
				+ snapshotPath + "\n";

		Files.write(cmdFile, commands.getBytes());

		ProcessBuilder pb = new ProcessBuilder(Constants.JPCONTROLLER_PATH, "-n", "-f",
				Constants.PROJECT_ROOT + "/Output/JProfiler/command.txt");

		Process p = pb.inheritIO().start();

		try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
			String line;
			while ((line = r.readLine()) != null) {
				System.out.println("[jpcontroller] " + line);
			}
		}

		int rc = p.waitFor();
		System.out.println("jpcontroller exit code: " + rc);
		System.out.println("snapshot exists: " + Files.exists(Paths.get(snapshotPath)));

	}

	// Step 7: Save profiler snapshot

	private void saveProfilerSnapshot() throws Exception {
		String outputDir = Constants.PROJECT_ROOT + File.separator + JPROFILER_OUTPUT_DIR;
		String snapshotPath = outputDir + SNAPSHOT_FILE;

		// Create output directory if it doesn't exist
		Files.createDirectories(Paths.get(outputDir));

		try {
			final List<String> cmdList = new ArrayList<>();
			cmdList.add("sudo");
			cmdList.add("-S");
			cmdList.add(Constants.JPCONTROLLER_PATH);
			cmdList.add("-n");
			cmdList.add("saveSnapshot");
			cmdList.add(snapshotPath);

			System.out.println("Saving snapshot to: " + snapshotPath);
			Process process = new ProcessBuilder(cmdList).inheritIO().start();
			int exitCode = process.waitFor();
			if (exitCode != 0) {
				System.err.println("saveSnapshot returned exit code: " + exitCode);
			}
		} catch (Exception e) {
			System.err.println("Error saving profiler snapshot: " + e.getMessage());
			throw e;
		}
	}

	// Step 8: Export profiler snapshot to CSVs

	public static int exportProfilerSnapshot() throws Exception {
		String outputDir = Constants.PROJECT_ROOT + "/" + JPROFILER_OUTPUT_DIR;
		String snapshotPath = outputDir + SNAPSHOT_FILE;

		try {
			final List<String> command = new ArrayList<>(Constants.JPEXPORT_COMMAND);

			System.out.println("Exporting snapshot to CSVs in: " + outputDir);
			Process process = new ProcessBuilder(command).inheritIO().start();
			int exitCode = process.waitFor();
			if (exitCode != 0) {
				System.err.println("JPController command '" + command + "' returned exit code: " + exitCode);
			}
			return exitCode;
		} catch (Exception e) {
			System.err.println("Error exporting snapshot to CSVs: " + e.getMessage());
			throw e;
		}
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
		command.add("-Djoularjx.config=" + Constants.PROJECT_ROOT + "/src/test/resources/config.properties");
		command.add("-javaagent:" + spectraAgentPath);
		command.add("-cp");
		// Include both main classes and test classes for JUnit execution
		String testClasses = "/Users/mac/Documents/RA/SPECTRA/target/test-classes";
		// Add JUnit and Hamcrest to classpath
		String junitJar = System.getProperty("user.home") + "/.m2/repository/junit/junit/4.13.2/junit-4.13.2.jar";
		String hamcrestJar = System.getProperty("user.home")
				+ "/.m2/repository/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar";
		command.add(aClasspath + File.pathSeparator + testClasses + File.pathSeparator + junitJar + File.pathSeparator
				+ hamcrestJar);
		command.add(aFQN);

		if (programArgs != null) {
			for (String arg : programArgs) {
				command.add(arg);
			}
		}

		System.out.println("Command: " + command);
		final ProcessBuilder processBuilder = new ProcessBuilder(command);
		processBuilder.redirectErrorStream(true);

		// Change Current Working Directory
		File newCurrentWorkingDirectory = new File("../Ptidej/ptidej-Ptidej/POM/");
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

	// Launch JPExport for converting profiler data
	private Process launchJpexport() throws IOException {
		final List<String> command = new ArrayList<>(Constants.JPEXPORT_COMMAND);
		System.out.println("JPExport command: " + command);
		return new ProcessBuilder(command).inheritIO().start();
	}

	// Consume process output streams asynchronously

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
			os.write("1234\n".getBytes());
			os.flush();
		}
	}

	/**
	 *
	 * The following code was added to support JMX-based control of JProfiler for
	 * starting/stopping recordings. So far, it is not needed now; but just kept
	 * just in case if we ever require to move to JMX control in the future.
	 */

	public long launchWithJMXProfiling(final ResultWriter aWriter, final String aClasspath, final String aFQN,
			final String... programArgs) throws Exception {

		System.out.println("=== JProfiler Profiling with JMX Control ===\n");

		// Step 1: Launch JVM with JProfiler agent (with auto-start recording)
		final String javaPath = System.getProperty("java.home");
		final String jprofilerAgent = Constants.JPROFILER_AGENT;

		System.out.println("Step 1: Launching JVM with JProfiler agent (auto-start profiling)...");
		final Process jvmProcess = launchJVMSuspended(javaPath, jprofilerAgent, aClasspath, aFQN, programArgs);

		// Step 2: Check port availability
		System.out.println("Step 2: Checking JProfiler port availability...");
		boolean portAvailable = waitForPortAvailable(JPROFILER_PORT, PORT_CHECK_TIMEOUT_MS);
		if (!portAvailable) {
			jvmProcess.destroyForcibly();
			throw new RuntimeException("JProfiler port " + JPROFILER_PORT + " not available");
		}

		// Step 2b: Check JMX port availability (crucial for JProfiler 14+)
		System.out.println("Step 2b: Checking JMX port availability...");
		boolean jmxPortAvailable = waitForPortAvailable(JMX_PORT, PORT_CHECK_TIMEOUT_MS);
		if (!jmxPortAvailable) {
			jvmProcess.destroyForcibly();
			throw new RuntimeException("JMX port " + JMX_PORT + " not available");
		}

		// Step 3: Connect to JProfiler via JMX
		// IMPORTANT: Use JProfiler's JMX port (8850), not the agent port (8849)
		System.out.println("Step 3: Connecting to JProfiler via JMX on port " + JMX_PORT + "...");
		JProfilerRemoteControllerClient controller = new JProfilerRemoteControllerClient("localhost", JMX_PORT);

		try {
			controller.connect();
			System.out.println("  Connected! Profiling is already running (auto-started with agent)");

			// Explicitly start recordings to ensure we capture data
			controller.startAllRecordings();

			VirtualMachine vm = null;
			// Wait for JDWP port to be ready before attaching debugger
			vm = attachDebugger("localhost", JDWP_PORT);
			System.out.println("Attached and listening for VMDeath events");

			attachShutdownListener(vm, aFQN, controller);
			vm.resume();

			// Step 5: Stop profiling recordings BEFORE saving snapshot
			System.out.println("\nStep 5: Stopping profiling recordings...");
//            controller.stopAllRecordings();
			int exitCode = 0;
			final long WAIT_TIMEOUT_MS = 60_000; // tune as needed
			if (jvmProcess.waitFor(WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
				exitCode = jvmProcess.exitValue();
				System.out.println("Profiled JVM exited within timeout with code: " + exitCode);
			} else {
				System.err.println("Timed out waiting for profiled JVM to exit after " + WAIT_TIMEOUT_MS
						+ "ms. Forcing termination...");
				// Optional: capture/process diagnostics here (thread dump, logs)
				if (jvmProcess.isAlive()) {
					jvmProcess.destroyForcibly();
				}
				// wait for forced termination to complete
				exitCode = jvmProcess.waitFor();
				System.out.println("Profiled JVM forcibly terminated, exitCode: " + exitCode);
			}

//            // Step 6: Save snapshot WHILE JVM IS STILL ALIVE
			System.out.println("\nStep 6: Saving snapshot...");
			String outputDir = Constants.PROJECT_ROOT + File.separator + JPROFILER_OUTPUT_DIR;
			String snapshotPath = outputDir + SNAPSHOT_FILE;
//            controller.saveSnapshot(snapshotPath);

			// Verify snapshot was created
			File snapshotFile = new File(snapshotPath);
			if (!snapshotFile.exists()) {
				throw new RuntimeException("Snapshot file was not created: " + snapshotPath);
			}
			System.out.println("  Snapshot file created: " + snapshotPath + " (" + snapshotFile.length() + " bytes)");

			// Step 7: Export to CSV
			System.out.println("\nStep 7: Exporting profiling data to CSV...");
			// controller.exportToCSV(snapshotPath, outputDir);

			System.out.println("\n===   SUCCESS ===");
			System.out.println("Profiling data saved to: " + outputDir);
			System.out.println("  - Snapshot: " + SNAPSHOT_FILE);
			System.out.println("  - CSVs: allobjects.csv, hotspots.csv, calltree.xml");

		} catch (Exception e) {
			System.err.println("\nError during JMX profiling: " + e.getMessage());
			e.printStackTrace();
			throw e;
		} finally {
			try {
				controller.close();
			} catch (Exception ex) {
				System.err.println("Warning: Error closing controller: " + ex.getMessage());
			}
			if (jvmProcess.isAlive()) {
				System.out.println("\nTerminating JVM process...");
				jvmProcess.destroyForcibly();
			}
		}
		return jvmProcess.pid();
	}

	private void attachShutdownListener(final VirtualMachine vm, final String mainClass,
			JProfilerRemoteControllerClient controller) {
		Thread eventThread = new Thread(() -> {
			try {
				EventRequestManager erm = vm.eventRequestManager();

				// Fire on main() exit
				MethodExitRequest mer = erm.createMethodExitRequest();
				mer.addClassFilter(mainClass);
				mer.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD); // suspend only main thread
				mer.enable();

				// Fallback: VMDeath
				VMDeathRequest vdr = erm.createVMDeathRequest();
				vdr.setSuspendPolicy(EventRequest.SUSPEND_NONE);
				vdr.enable();

				EventQueue queue = vm.eventQueue();

				boolean done = false;
				while (!done) {
					EventSet eventSet = queue.remove(); // blocking
					for (Event event : eventSet) {

						if (event instanceof MethodExitEvent mee) {
							if ("main".equals(mee.method().name())
									&& mainClass.equals(mee.method().declaringType().name())) {

								System.out.println("main() exited — PAUSING JVM for snapshot...");

								// JVM main thread is suspended here!
								Thread.sleep(5000); // <-- THIS IS THE MAGIC "pause before exit"

								controller.stopAllRecordings();

								// Step 6: Save snapshot WHILE JVM IS STILL ALIVE
								System.out.println("\nStep 6: Saving snapshot...");
								String outputDir = Constants.PROJECT_ROOT + File.separator + JPROFILER_OUTPUT_DIR;
								String snapshotPath = outputDir + SNAPSHOT_FILE;
								controller.saveSnapshot(snapshotPath);
								controller.exportToCSV(snapshotPath, outputDir);
								done = true;
							}
						}

						if (event instanceof VMDeathEvent || event instanceof VMDisconnectEvent) {
							System.out.println("VMDeath fallback");
							// Still attempt cleanup, but might be too late.
							// runProfilerCleanupAPI();
							controller.stopAllRecordings();

							// Step 6: Save snapshot WHILE JVM IS STILL ALIVE
							System.out.println("\nStep 6: Saving snapshot...");
							String outputDir = Constants.PROJECT_ROOT + File.separator + JPROFILER_OUTPUT_DIR;
							String snapshotPath = outputDir + SNAPSHOT_FILE;
							controller.saveSnapshot(snapshotPath);
							controller.exportToCSV(snapshotPath, outputDir);

							done = true;
						}
					}

					// Resume suspended threads (if main was suspended)
					eventSet.resume();
				}

			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				try {
					vm.dispose();
				} catch (Throwable ignored) {
				}
			}
		});
	}

	static class JProfilerRemoteControllerClient {
		private final String host;
		private final int jmxPort;
		private MBeanServerConnection connection;
		private JMXConnector connector;
		private boolean connected = false;
		private static final String CONTROLLER_MBEAN = "com.jprofiler.api.agent.mbean:type=RemoteController";

		public JProfilerRemoteControllerClient(String host, int jmxPort) {
			this.host = host;
			this.jmxPort = jmxPort;
		}

		public void connect() throws Exception {
			String url = "service:jmx:rmi:///jndi/rmi://" + host + ":" + jmxPort + "/jmxrmi";
			System.out.println("Connecting to JProfiler at: " + url);

			JMXServiceURL jmxUrl = new JMXServiceURL(url);
			connector = JMXConnectorFactory.connect(jmxUrl);
			connection = connector.getMBeanServerConnection();
			connected = true;

			System.out.println(" Connected to JProfiler RemoteController MBean");
		}

		/**
		 * Start all profiling recordings (CPU, Allocation, Monitor)
		 */
		public void startAllRecordings() throws Exception {
			ensureConnected();
			System.out.println("Starting all profiling recordings...");

			startCPU();
			System.out.println("   CPU recording started");

			startAlloc();
			System.out.println("   Memory allocation recording started");

			startMonitor();
			System.out.println("   Monitor recording started");

			System.out.println(" All recordings started successfully");
		}

		/**
		 * Stop all profiling recordings
		 */
		public void stopAllRecordings() throws Exception {
			ensureConnected();
			System.out.println("Stopping all profiling recordings...");

			stopCPU();
			System.out.println(" CPU recording stopped");

			stopAlloc();
			System.out.println("   Memory allocation recording stopped");

			stopMonitor();
			System.out.println("   Monitor recording stopped");

			System.out.println(" All recordings stopped successfully");
		}

		public void startCPU() throws Exception {
			ensureConnected();
			connection.invoke(new ObjectName(CONTROLLER_MBEAN), "startCPURecording", new Object[] { Boolean.TRUE },
					new String[] { "boolean" });
		}

		public void stopCPU() throws Exception {
			ensureConnected();
			connection.invoke(new ObjectName(CONTROLLER_MBEAN), "stopCPURecording", null, null);
		}

		public void startAlloc() throws Exception {
			ensureConnected();
			connection.invoke(new ObjectName(CONTROLLER_MBEAN), "startAllocRecording", new Object[] { Boolean.TRUE },
					new String[] { "boolean" });
		}

		public void stopAlloc() throws Exception {
			ensureConnected();
			connection.invoke(new ObjectName(CONTROLLER_MBEAN), "stopAllocRecording", null, null);
		}

		public void startMonitor() throws Exception {
			ensureConnected();
			connection.invoke(new ObjectName(CONTROLLER_MBEAN), "startMonitorRecording", null, null);
		}

		public void stopMonitor() throws Exception {
			ensureConnected();
			connection.invoke(new ObjectName(CONTROLLER_MBEAN), "stopMonitorRecording", null, null);
		}

		public void stopThreadProfiling() throws Exception {
			ensureConnected();
			connection.invoke(new ObjectName(CONTROLLER_MBEAN), "stopThreadProfiling", null, null);
		}

		public void saveSnapshot(String path) throws Exception {
			ensureConnected();

			// Create parent directory if needed
			File snapshotFile = new File(path);
			Files.createDirectories(snapshotFile.getParentFile().toPath());

			System.out.println("Saving snapshot to: " + path);
			connection.invoke(new ObjectName(CONTROLLER_MBEAN), "saveSnapshot", new Object[] { path },
					new String[] { "java.lang.String" });
			System.out.println(" Snapshot saved successfully");
		}

		public void exportToCSV(String snapshotPath, String outputDir) throws Exception {
			System.out.println("Exporting snapshot to CSV...");

			// Create output directory
			Files.createDirectories(Paths.get(outputDir));

			// Build jpexport command
			List<String> command = new ArrayList<>();
			command.add(Constants.JPCONTROLLER_PATH.replace("jpcontroller", "jpexport"));
			command.add(snapshotPath);

			// Export AllObjects view
			command.add("AllObjects");
			command.add("-format=csv");
			command.add(outputDir + "/allobjects.csv");

			// Export Hotspots view
			command.add("Hotspots");
			command.add("-format=csv");
			command.add(outputDir + "/hotspots.csv");

			// Export CallTree
			command.add("CallTree");
			command.add("-format=xml");
			command.add("-aggregation=method");
			command.add(outputDir + "/calltree.xml");

			System.out.println("Executing: " + command);

			Process process = new ProcessBuilder(command).redirectErrorStream(true).start();

			// Capture output
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				String line;
				while ((line = reader.readLine()) != null) {
					System.out.println("  [jpexport] " + line);
				}
			}

			int exitCode = process.waitFor();
			if (exitCode == 0) {
				System.out.println(" CSV export completed successfully");
				System.out.println("  Output directory: " + outputDir);
			} else {
				System.err.println("Warning: jpexport returned exit code: " + exitCode);
			}
		}

		public boolean isConnected() {
			return connected;
		}

		private void ensureConnected() {
			if (!connected) {
				throw new IllegalStateException("Not connected to JProfiler. Call connect() first.");
			}
		}

		public void close() {
			if (connector != null) {
				try {
					connector.close();
					connected = false;
					System.out.println(" Disconnected from JProfiler");
				} catch (Exception e) {
					System.err.println("Warning: Error closing JMX connector: " + e.getMessage());
				}
			}
		}
	}
}
