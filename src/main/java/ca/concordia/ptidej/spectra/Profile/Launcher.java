package ca.concordia.ptidej.spectra.Profile;

import java.io.*;
import java.net.Socket;
import java.nio.file.*;
import java.util.*;
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
import ca.concordia.ptidej.spectra.analysis.CSVMerger.MethodData;

import static ca.concordia.ptidej.spectra.analysis.CSVMerger.csvEscape;
import static ca.concordia.ptidej.spectra.analysis.CSVMerger.normalizeSignature;

public class Launcher {

	private static final int JPROFILER_PORT = 8849;
	private static final int JMX_PORT = 8850; // JProfiler MBean/JMX port (Must be different from JPROFILER_PORT)
	private static final int JDWP_PORT = 5005;
	private static final int PORT_CHECK_TIMEOUT_MS = 30000; // 30 seconds
	private static final int PORT_CHECK_INTERVAL_MS = 500; // Check every 500ms
	private static final String JPROFILER_OUTPUT_DIR = "Output/JProfiler/";
	private static final String SNAPSHOT_FILE = "snapshot.jps";
    private static final int NUM_JOULARJX_RUNS = 3;
    private static final int NUM_JPROFILER_RUNS = 1;

    private final AtomicBoolean cleanupRun = new AtomicBoolean(false);
    
    private static void cleanupPorts() {
        int[] ports = {JPROFILER_PORT, JDWP_PORT};
        System.out.println("[Launcher] Cleaning up ports: " + Arrays.toString(ports));
        for (int port : ports) {
            try {
                // Try normal kill first
                String[] cmd = {"bash", "-c", "lsof -ti:" + port + " | xargs kill -9 2>/dev/null || true"};
                new ProcessBuilder(cmd).start().waitFor(2, TimeUnit.SECONDS);
                
                // If still alive, try sudo
                String[] sudoCmd = {"bash", "-c", "echo \"1234\" | sudo -S lsof -ti:" + port + " | xargs echo \"1234\" | sudo -S kill -9 2>/dev/null || true"};
                new ProcessBuilder(sudoCmd).start().waitFor(2, TimeUnit.SECONDS);
            } catch (Exception ignored) {}
        }
    }

	public long launch(final ResultWriter aWriter, final String aClasspath, final String aFQN,
			final String... programArgs) throws Exception {

        prepareJProfilerDataDirectory();
        List<Path> jprofilerXmls = new ArrayList<>();
//         1. Phase 1: JProfiler Instrumentation & Calibration
        System.out.println("\n=== PHASE 1: JProfiler Instrumentation ===");
        long pid1 = launchJProfilerPhase(Constants.JPROFILER_INSTRUMENTATION_CONFIG, aClasspath, aFQN, programArgs);
        if (pid1 <= 0) {
            System.err.println("Warning: Phase 1 Instrumentation returned non-positive PID");
        }
        Path phase1Xml = copyExportedCalltreeXML(0); // Run 0 for phase 1
        Path phase1Target = Paths.get(Constants.PHASE1_CALLTREE_XML);
        Files.move(phase1Xml, phase1Target, StandardCopyOption.REPLACE_EXISTING);

        // Phase 1 provides the calibration data
        int nRuns = calibrateSamplingRuns(phase1Target);
        System.out.println("Will perform " + nRuns + " sampling runs for statistical significance...");

        // 2. Phase 2: JProfiler Sampling
        System.out.println("\n=== PHASE 2: JProfiler Sampling (" + nRuns + " runs) ===");
        for (int run = 1; run <= nRuns; run++) {
            System.out.println("\n--- JProfiler Sampling run " + run + " / " + nRuns + " ---");
            long pid = launchJProfilerPhase(Constants.JPROFILER_SAMPLING_CONFIG, aClasspath, aFQN, programArgs);
            if (pid <= 0) {
                System.err.println("Warning: JProfiler run " + run + " returned non-positive PID");
            } else {
                try {
                    Path xmlPath = copyExportedCalltreeXML(run);
                    jprofilerXmls.add(xmlPath);
                } catch (IOException e) {
                    System.err.println("Warning: failed to copy calltree for run " + run + ": " + e.getMessage());
                }
            }
        }

        System.out.println("=== Phase 2: Completed " + jprofilerXmls.size() + " sampling runs ===");
        if (!jprofilerXmls.isEmpty()) {
            try {
                averageCalltreeXMLs(jprofilerXmls);
                System.out.println("JProfiler sampling data averaged successfully.");
            } catch (Exception e) {
                System.err.println("Error averaging JProfiler XMLs: " + e.getMessage());
            }
        }
            // Phase 3: JoularJX sampling
        System.out.println("\n=== PHASE 3: Running JoularJX " + nRuns + " times ===");
        List<Long> joularProcessIds = new ArrayList<>();
        prepareJoularJXDataDirectory();
        for (int run = 1; run <= nRuns; run++) {
            System.out.println("\n--- JoularJX Run " + run + "/" + nRuns + " ---");
            long joularProcessId = launchJoularjxPhase(aClasspath, aFQN, programArgs);

            if (joularProcessId > 0) {
                joularProcessIds.add(joularProcessId);
                System.out.println("Finished JoularJX Run " + run + " with PID: " + joularProcessId);


                  // Robust JoularJX result management using absolute paths
                  String destPath = new File(Constants.JOULARJX_DIR + File.separator + run + "-joularJX-123-all-methods-energy.csv").getAbsolutePath();

                  String[] moveCmd = {
                      "bash", "-c",
                      "echo \"1234\" | sudo -S find " + Constants.PROJECT_ROOT + " -maxdepth 3 -name \"joularJX-123-*-methods-energy.csv\" -size +0c -exec mv {} " + destPath + " \\; 2>/dev/null; " +
                      "echo \"1234\" | sudo -S chown mac " + destPath + " 2>/dev/null; " +
                      "echo \"1234\" | sudo -S rm -rf " + Constants.PROJECT_ROOT + "/joularjx-result " + Constants.PROJECT_ROOT + "/joularJX-result; " +
                      "echo 'MOVE_FINISHED'"
                  };

                  try {
                      Process p = new ProcessBuilder(moveCmd).start();
                      consumeProcessOutput(p);
                      p.waitFor();
                      if (new File(destPath).exists()) {
                          System.out.println("[Launcher] Successfully moved results for run " + run);
                      } else {
                          // Fallback check: if no non-empty file found, check for ANY file
                          System.err.println("[Launcher] Warning: No non-empty JoularJX result found after run " + run + ". Checking for empty files...");
                          String[] fallbackMove = {
                              "bash", "-c",
                              "echo \"1234\" | sudo -S find " + Constants.PROJECT_ROOT + " -maxdepth 3 -name \"joularJX-123-all-methods-energy.csv\" -exec mv {} " + destPath + " \\; 2>/dev/null"
                          };
                          new ProcessBuilder(fallbackMove).start().waitFor();
                          if (new File(destPath).exists()) {
                              System.out.println("[Launcher] Moved empty JoularJX result for run " + run);
                          } else {
                             System.err.println("[Launcher] JoularJX result file not found after run " + run);
                          }
                      }
                  } catch (Exception e) {
                      System.err.println("[Launcher] Error during result move: " + e.getMessage());
                  }
            } else {
                System.err.println("Warning: JoularJX Run " + run + " failed");
            }
        }

        if (joularProcessIds.isEmpty()) {
            throw new RuntimeException("All JoularJX runs failed");
        }

        System.out.println("\n=== All JoularJX runs completed successfully ===");

        // Average JoularJX results from all runs
        try {
            averageJoularJXResults(joularProcessIds);
            updateEnergyCSVPath();
            System.out.println("Energy data averaged successfully");
        } catch (Exception e) {
            System.err.println("Error averaging JoularJX results: " + e.getMessage());
            throw new IOException("Failed to average JoularJX results", e);
        }

		// 4. Phase 4: Merge CSV results
		if (programArgs.length > 0 || !joularProcessIds.isEmpty()) {
			String mergeArg = (programArgs != null && programArgs.length > 0) ? Arrays.toString(programArgs) : aFQN;
			CSVMerger.runCSVMerger(mergeArg);
		}
        return joularProcessIds.get(0);

	}

    private int calibrateSamplingRuns(Path phase1Xml) throws Exception {
        Map<String, MethodData> data = CSVMerger.parseXMLData(phase1Xml.toString());
        double minDuration = Double.MAX_VALUE;
        
        for (MethodData md : data.values()) {
            if (md.invocations > 0) {
                double duration = md.executionTime / (double) md.invocations;
                if (duration > 0 && duration < minDuration) {
                    minDuration = duration;
                }
            }
        }

        // Ignore extremely fast methods (noise) during calibration
        if (minDuration < 1.0) {
            System.out.println("Warning: Shortest method duration " + minDuration + " us is very low. Using 1.0 us for calibration.");
            minDuration = 1.0;
        }
        
        if (minDuration == Double.MAX_VALUE) {
            System.out.println("Could not find a valid method duration for calibration. Defaulting to 5 runs.");
            return 5;
        }
        // Formula: runs = max(sample_period / shortest_lifetime, 1) * safety_multiplier
        // sample_period = 1ms = 1000us
        // safety_multiplier = 5
        double samplePeriodUs = 1000.0;
        int safetyMultiplier = 5;

        int calculatedRuns = (int) (Math.max(samplePeriodUs / minDuration, 1.0) * safetyMultiplier);
        
        // Cap runs to a reasonable maximum (e.g., 50) to prevent excessive execution time
        int nRuns = Math.min(calculatedRuns, 50);
        
        System.out.printf("Shortest method duration: %.2f us, calculated runs: %d, capped runs: %d (sample_period: %.0f us, safety_multiplier: %d)%n", 
                minDuration, calculatedRuns, nRuns, samplePeriodUs, safetyMultiplier);
        return nRuns;
    }

	private long launchJProfilerPhase(final String configPath, final String aClasspath, final String aFQN, final String... programArgs)
			throws IOException {
		System.out.println("=== JProfiler Profiling Session ===");
		cleanupPorts();

		// Step 1: Launch JVM in suspended mode
		final String javaPath = System.getProperty("java.home");
		final String jprofilerAgent = Constants.JPROFILER_AGENT;

		System.out.println("Step 1: Launching JVM in suspended mode with JProfiler...");
		final Process jvmProcess = launchJVMSuspended(javaPath, jprofilerAgent, configPath, aClasspath, aFQN, programArgs);

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
			Thread.sleep(3000); // Reduced from 10000
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		int exitCode = 0;
		try {
			boolean finished = jvmProcess.waitFor(300, TimeUnit.SECONDS);
            if (!finished) {
                System.err.println("[Launcher] Profiled JVM HUNG. Killing process " + jvmProcess.pid());
                jvmProcess.destroyForcibly();
                exitCode = -1;
            } else {
                exitCode = jvmProcess.exitValue();
                System.out.println("Profiled JVM exited with code: " + exitCode);
            }
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		return jvmProcess.pid();
	}

	// Step 1.1: Launch JVM in suspended mode with JProfiler agent
	private Process launchJVMSuspended(final String javaPath, final String jprofilerAgent, final String configPath, final String classpath,
			final String mainClass, final String... programArgs) throws IOException {
		final List<String> command = new ArrayList<>();
		command.add(javaPath + "/bin" + "/java");

		// JProfiler agent with suspended mode and specific port
		command.add("-agentpath:" + jprofilerAgent + "=port=" + JPROFILER_PORT + ",nowait,config=" + configPath);
        // De-optimization flags for 1:1 overlap
        command.add("-XX:+UnlockDiagnosticVMOptions");
        command.add("-XX:-UseLibmIntrinsic");
        command.add("-XX:-UseMathExactIntrinsics");
        command.add("-XX:-Inline");

		command.add("-cp");
		String jprofilerApiJar = "/Applications/JProfiler.app/Contents/Resources/app/api/jprofiler-controller.jar";
		command.add(classpath + java.io.File.pathSeparator + jprofilerApiJar);
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

		// Start the process
		final Process process = processBuilder.start();

		System.out.println("Launched JVM in suspended mode with PID: " + process.pid());

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
			// executeJpcontrollerCommand("startAllocRecording", "true", jvmPid);
			// executeJpcontrollerCommand("startMonitorRecording", "", jvmPid);
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
			// executeJpcontrollerCommand("stopAllocRecording", "", jvmPid);
			// executeJpcontrollerCommand("stopMonitorRecording", "", jvmPid);

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


	// Step 7: Save profiler snapshot


	// Step 8: Export profiler snapshot to CSVs

	public static int exportProfilerSnapshot() throws Exception {
		String outputDir = Constants.PROJECT_ROOT + "/" + JPROFILER_OUTPUT_DIR;
		String snapshotPath = outputDir + SNAPSHOT_FILE;

		try {
			final List<String> command = new ArrayList<>(Constants.JPEXPORT_COMMAND);

			System.out.println("Exporting snapshot to CSVs in: " + outputDir);
			Process process = new ProcessBuilder(command).start();
			consumeProcessOutput(process);
			boolean completed = process.waitFor(120, TimeUnit.SECONDS);
			if (!completed) {
				System.err.println("Timeout waiting for exportProfilerSnapshot. Destroying process.");
				process.destroyForcibly();
				return -1;
			}
			int exitCode = process.exitValue();
			if (exitCode != 0) {
				System.err.println("JPExport command returned exit code: " + exitCode);
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
		final String spectraAgentPath = Constants.PROJECT_ROOT + "/src/main/resources/joularjx-3.1.0.jar";

		final List<String> command = new ArrayList<>();
		command.add("sudo");
		command.add("-S");
		command.add(javaPath + File.separator + "bin" + File.separator + "java");
		command.add("-Djoularjx.config=" + Constants.PROJECT_ROOT + "/src/test/resources/config.properties");
		command.add("-javaagent:" + spectraAgentPath);
        // De-optimization flags for 1:1 overlap
        command.add("-XX:+UnlockDiagnosticVMOptions");
        command.add("-XX:-UseLibmIntrinsic");
        command.add("-XX:-UseMathExactIntrinsics");
        command.add("-XX:-Inline");
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

		// Start the process
		final Process process = processBuilder.start();

		// Enter sudo password
		enterPassword(process);

		// Consume output in background
		consumeProcessOutput(process);

		try {
			boolean finished = process.waitFor(300, TimeUnit.SECONDS);
            if (!finished) {
                System.err.println("[Launcher] JoularJX process HUNG. Killing process " + process.pid());
                process.destroyForcibly();
            } else {
                System.out.println("Joularjx process exited with code: " + process.exitValue());
            }
            
            // The move logic is handled in the main launch loop for multiple runs.
            
		} catch (InterruptedException e) {
			System.err.println("Joularjx process wait was interrupted");
			Thread.currentThread().interrupt();
			throw new RuntimeException(e);
		}

		return process.pid();
	}


	// Consume process output streams asynchronously

	private static void consumeProcessOutput(final Process process) {
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
	private static void enterPassword(final Process process) throws IOException {
		try (OutputStream os = process.getOutputStream()) {
			// Replace with your actual password handling mechanism
			os.write("1234\n".getBytes());
			os.flush();
		}
	} /**
     * Average JoularJX energy results from multiple runs
     */
    private void averageJoularJXResults(List<Long> processIds) throws IOException {

        Path dataDir = Paths.get("Output", "JoularJX"); 
        Files.createDirectories(dataDir);
        if (!Files.isWritable(dataDir)) {
            System.err.println("Permission denied: cannot write to directory: " + dataDir);
            try {
                System.err.println("Owner: " + Files.getOwner(dataDir));
            } catch (Exception ignored) {}
            throw new IOException("Directory not writable: " + dataDir);
        }
        Map<String, EnergyData> energyMap = new HashMap<>();        // Read all energy CSV files
        for (int run = 1; run <= processIds.size(); run++) {
            String energyFilePath = "Output/JoularJX" + File.separator + run + "-joularJX-123-all-methods-energy.csv";
            File energyFile = new File(energyFilePath);

            if (!energyFile.exists()) {
                System.err.println("Warning: Energy file not found: " + energyFilePath);
                continue;
            }

            System.out.println("Reading energy data from: " + energyFilePath);
            Map<String, Double> runEnergy = new HashMap<>();
            try (BufferedReader br = new BufferedReader(new FileReader(energyFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    int lastCommaIndex = line.lastIndexOf(',');
                    if (lastCommaIndex < 0) continue;

                    String methodSignature = line.substring(0, lastCommaIndex).trim();
                    String energyStr = line.substring(lastCommaIndex + 1).trim();

                    try {
                        double energy = Double.parseDouble(energyStr);
                        // Sum duplicates within the SAME run file
                        runEnergy.merge(methodSignature, energy, Double::sum);
                    } catch (NumberFormatException e) {
                        // Skip invalid lines
                    }
                }
            }
            
            // Add summed run values to global average map
            for (Map.Entry<String, Double> entry : runEnergy.entrySet()) {
                energyMap.computeIfAbsent(entry.getKey(), k -> new EnergyData())
                        .addValue(entry.getValue());
            }
        }

        System.out.println("Averaged " + energyMap.size() + " method signatures across "
                + processIds.size() + " runs");
        // Write averaged results
        Path outputFile = dataDir.resolve("joularJX-averaged-all-methods-energy.csv");

        System.out.println("Writing averaged energy data to: " + outputFile);

        try (BufferedWriter writer = Files.newBufferedWriter(outputFile, java.nio.charset.StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
            for (Map.Entry<String, EnergyData> entry : energyMap.entrySet()) {
                writer.write(entry.getKey() + "," + entry.getValue().getAverage());
                writer.newLine();
            }
        }

        System.out.println("Averaged " + energyMap.size() + " method signatures across "
                + processIds.size() + " runs");
    }


    /**
     * Copy averaged energy file to standard location expected by CSVMerger
     */
    private void updateEnergyCSVPath() throws IOException {
        String averagedFile = Constants.JOULARJX_AVG_CSV;
        String targetFile = Constants.PROJECT_ROOT + "/Output/JoularJX/joularJX-123-all-methods-energy.csv";
 
        Files.copy(Paths.get(averagedFile), Paths.get(targetFile),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Copied averaged file to: " + targetFile);
    }

    /**
     * Helper class to accumulate energy values and compute average
     */
    private static class EnergyData {
        private double sum = 0.0;
        private int count = 0;

        public void addValue(double value) {
            sum += value;
            count++;
        }

        public double getAverage() {
            return count > 0 ? sum / count : 0.0;
        }
    }


    private void averageCalltreeXMLs(List<Path> xmlPaths) throws Exception {
        if (xmlPaths.isEmpty()) throw new IOException("No XMLs");

        Map<String, double[]> stats = new HashMap<>();  // method -> [sum_count, sum_time, sum_self, runs]

        for (Path xml : xmlPaths) {
            // Use YOUR existing parser!
            Map<String, MethodData> runData = CSVMerger.parseXMLData(xml.toString());

            for (Map.Entry<String, MethodData> entry : runData.entrySet()) {
                String methodKey = entry.getKey();
                MethodData data = entry.getValue();

                double[] vals = stats.computeIfAbsent(methodKey, k -> new double[4]);
                vals[0] += data.invocations;  // From your XML: count="1"
                vals[1] += data.executionTime; // From your XML: time="170883894"
                vals[2] += data.selfTime;      // From your XML: selfTime="4862"
                vals[3] += 1;                  // Number of runs
            }
        }

        // Write averaged CSV for CSVMerger
        Path avgCsv = Paths.get(Constants.PROJECT_ROOT, "Output", "JProfiler", "calltree-averaged.csv");
        try (PrintWriter out = new PrintWriter(avgCsv.toFile())) {
            out.println("method_key|avg_invocations|avg_executionTime|avg_selfTime");
            for (Map.Entry<String, double[]> e : stats.entrySet()) {
                double[] v = e.getValue();
                out.printf("%s|%.1f|%.0f|%.1f%n", e.getKey(),
                        v[0]/v[3], v[1]/v[3], v[2]/v[3]);
            }
        }
        System.out.println("XML averaged " + stats.size() + " methods from " + xmlPaths.size() + " runs");
    }



    private void prepareJProfilerDataDirectory() throws IOException {
        Path dataDir = Paths.get("Output", "JProfiler");
        Files.createDirectories(dataDir);

        // Delete previous per-run files so a new batch overwrites old data
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dataDir, "calltree-run*.csv")) {
            for (Path p : ds) {
                Files.deleteIfExists(p);
            }
        } catch (IOException ignored) {
            // nothing to delete
        }

        // Remove previous averaged file
        Files.deleteIfExists(dataDir.resolve("calltree-averaged.csv"));
    }

    private void prepareJoularJXDataDirectory() throws IOException {
        Path dataDir = Paths.get("Output", "JoularJX");
        Files.createDirectories(dataDir);

        // Delete previous per-run files so a new batch overwrites old data
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dataDir, "*-joularJX-123-all-methods-energy.csv")) {
            for (Path p : ds) {
                Files.deleteIfExists(p);
            }
        } catch (IOException ignored) {
            // nothing to delete
        }

        // Remove previous averaged file
        Files.deleteIfExists(dataDir.resolve("joularJX-averaged-all-methods-energy.csv"));
    }
    private Path copyExportedCalltreeXML(final int run) throws IOException {
        Path jprofOut = Paths.get(Constants.PROJECT_ROOT, "Output", "JProfiler");
        Path dataDir = Paths.get(Constants.PROJECT_ROOT, "Output", "JProfiler");
        Files.createDirectories(dataDir);

        Path src = jprofOut.resolve("calltree.csv.xml");  // Your existing XML
        if (!Files.exists(src)) {
            throw new IOException("calltree.csv.xml not found: " + src);
        }

        Path target = dataDir.resolve("calltree-run-" + run + ".xml");
        Files.copy(src, target, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Copied XML " + src.getFileName() + " -> " + target);
        return target;
    }



    private void deleteDirectory(File directory) {
        File[] allContents = directory.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        directory.delete();
    }
}
