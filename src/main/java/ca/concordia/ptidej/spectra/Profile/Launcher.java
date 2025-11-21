package ca.concordia.ptidej.spectra.Profile;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.jdi.event.*;
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
    private static final int JMX_PORT = 8849; // JProfiler MBean/JMX port
    private static final int JDWP_PORT = 5005;
    private static final int PORT_CHECK_TIMEOUT_MS = 30000; // 30 seconds
    private static final int PORT_CHECK_INTERVAL_MS = 500; // Check every 500ms
    private static final String JPROFILER_OUTPUT_DIR = "Output/JProfiler/";
    private static final String SNAPSHOT_FILE = "snapshot.jps";

    private final AtomicBoolean cleanupRun = new AtomicBoolean(false);

    public void launch(final ResultWriter aWriter, final String aClasspath,
            final String aFQN, final String... programArgs) throws IOException {

        // 1. Launch JProfiler
        final long jprofilerPid = this.launchJProfilerPhase(aClasspath, aFQN,
                programArgs);

        if (jprofilerPid > 0) {
            System.out.println("Finished Executing JProfiler with PID: "
                    + jprofilerPid);

            // 2. Launch JPExport for profiling data conversion
            System.out.println("Launching JPExport...");
            Process jpexport = launchJpexport();
            try {
                jpexport.waitFor();
                System.out.println("JPExport completed successfully");
            } catch (InterruptedException e) {
                System.err.println("JPExport was interrupted");
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        } else {
            throw new RuntimeException("Failed to launch JProfiler.");
        }

        // 3. Launch JVM with Joularjx and Spectra agent
        System.out.println("Launching Joularjx with Spectra agent...");
        long joularProcessId = launchJoularjxPhase(aClasspath, aFQN,
                programArgs);

        if (joularProcessId > 0) {
            System.out.println("Finished Executing Joular with PID: "
                    + joularProcessId);
            System.out.println("Launched all JVMs and tools successfully.");
        } else {
            throw new RuntimeException("Failed to launch Joular.");
        }

        // 4. Merge CSV results
        if (programArgs.length > 0 || joularProcessId > 0) {
            CSVMerger.runCSVMerger(Arrays.toString(programArgs) + " " + aFQN);
        }
    }


    private long launchJProfilerPhase(final String aClasspath,
            final String aFQN, final String... programArgs) throws IOException {
        System.out.println("=== PHASE 1: JProfiler Profiling ===");

        // Step 1: Launch JVM in suspended mode
        final String javaPath = System.getProperty("java.home");
        final String jprofilerAgent = Constants.JPROFILER_AGENT;

        System.out.println("Step 1: Launching JVM in suspended mode with JProfiler...");
        final Process jvmProcess = launchJVMSuspended(javaPath, jprofilerAgent,
                aClasspath, aFQN, programArgs);

        System.out.println("Step 2: Checking if JProfiler port is available...");
        // Step 2: Wait for JProfiler port to be available
        boolean portAvailable = waitForPortAvailable(JPROFILER_PORT,
                PORT_CHECK_TIMEOUT_MS);
        if (!portAvailable) {
            jvmProcess.destroyForcibly();
            throw new RuntimeException(
                    "JProfiler port " + JPROFILER_PORT
                            + " did not become available within "
                            + PORT_CHECK_TIMEOUT_MS + "ms");
        }
        System.out.println("JProfiler port " + JPROFILER_PORT + " is available");

        System.out.println("Step 3: Attaching JPController and starting profiling...");
        // Step 3: Attach via JDI and listen for child VM termination, do not dispose vm
        // so events are delivered
        VirtualMachine vm = null;
        try {
            vm = attachDebuggerAndResume("localhost", JDWP_PORT);
            System.out.println("Attached and listening for VMDeath events");
        } catch (Exception e) {
            jvmProcess.destroyForcibly();
            throw new RuntimeException("Failed to attach JPController: " + e.getMessage(), e);
        }
        System.out.println("Step 4: Now start profiling *before* letting JVM run...");

        startProfilingRecordingsByAPI();
        System.out.println("Step 5: Attaching Shutdown Listener for profiling cleanup...");
        // Start event thread to listen for JVM termination
        attachShutdownListenerAndProfiling(vm);

        // Resume target VM now that recordings are configured
        System.out.println("Step 6: Resuming target VM now...");
        vm.resume();

        // Wait for natural termination; JDI event thread will handle cleanup on
        // VMDeath.
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

//     Step 1.1: Launch JVM in suspended mode with JProfiler agent
    private Process launchJVMSuspended(final String javaPath,
            final String jprofilerAgent, final String classpath,
            final String mainClass, final String... programArgs)
            throws IOException {
        final List<String> command = new ArrayList<>();
        command.add(javaPath + "/bin" + "/java");

        // JProfiler agent with suspended mode and specific port
        command.add("-agentpath:" + jprofilerAgent
                + "=port=" + JPROFILER_PORT
                + ",config=" + Constants.PROJECT_ROOT
                + "/src/main/resources/jprofiler_config.xml");

        command.add("-cp");
        command.add(classpath);
        command.add("-Djdk.attach.allowAttachSelf=true");
        command.add("-agentlib:jdwp=transport=dt_socket,server=y,address=*:5005,suspend=y");
        command.add(mainClass);

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
        // Start the process
        final Process process = processBuilder.start();

        // Provide the sudo password
        enterPassword(process);
        System.out.println("Launched JVM in suspended mode with PID: " + process.pid() + " at directory:" +
                newCurrentWorkingDirectory.getPath());

        // Consume output in background thread
        consumeProcessOutput(process);

        return process;
    }

//     Step 2.1: Wait for JProfiler port to become available
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
                stopProfiling();

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


//     *  Programmatically attach debugger (JDWP) and resume the suspended JVM
//     * using JDI
    private VirtualMachine attachDebuggerAndResume(final String host, final int port)
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
        ;
        return vm;
    }

//      Issue start Recording commands to JProfiler agent)
    private void startProfilingRecordingsByAPI() {
        try {
            System.out.println("Starting profiling recordings via jpcontroller CLI...");
            executeJpcontrollerCommand("startCPURecording", "true");
            executeJpcontrollerCommand("startAllocRecording", "true");
            executeJpcontrollerCommand("startMonitorRecording", "");
            executeJpcontrollerCommand("startThreadProfiling", ""); // Optional,
            System.out.println("Profiling recordings started (CLI)");
            Process jpcontroller = null;
            try {
                jpcontroller = new ProcessBuilder(Constants.JPCONTROLLER_PATH,
                        "-n", "-f", Constants.PROJECT_ROOT + "/Output/JProfiler/command.txt").inheritIO().start();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to start profiling recordings via CLI: " + e.getMessage(), e);
        }
    }

    private void attachShutdownListenerAndProfiling(final VirtualMachine vm) {
        Thread eventThread = new Thread(() -> {
            try {
                EventQueue queue = vm.eventQueue();
                boolean done = false;
                while (!done) {
                    EventSet eventSet = queue.remove();
                    for (Event event : eventSet) {
                        if (event instanceof VMDeathEvent || event instanceof VMDisconnectEvent) {
                            System.out.println("VM terminated, running profiling cleanup...");
                            runProfilerCleanupAPI();
                            done = true;
                        }
                    }
                    // eventSet.resume();
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    vm.dispose();
                } catch (Throwable t) {
                }
            }
        });
        eventThread.setDaemon(true);
        eventThread.start();
    }

    // Handles stop recording, save snapshot, export CSVs by API (not CLI)
    // Use your JProfilerRemoteControllerClient or similar here.
    private void runProfilerCleanupAPI() {
        try {
            // Cleanup can use JMX if the JVM is still running, but on VMDeath it might be
            // too late for JMX.
            // Ideally, we should use CLI for cleanup too if JMX is flaky at shutdown.
            // For now, let's try CLI for cleanup as well to be safe and consistent.
            System.out.println("Stopping profiling via jpcontroller CLI...");

            String outputDir = Constants.PROJECT_ROOT + File.separator + JPROFILER_OUTPUT_DIR;
            Files.createDirectories(Paths.get(outputDir));
            String snapshotPath = outputDir + SNAPSHOT_FILE;
            System.out.println("Stop recordings and save snapshot to: " + snapshotPath);
            executeJpcontrollerCommand("stopCPURecording", "");
            executeJpcontrollerCommand("stopAllocRecording", "");
            executeJpcontrollerCommand("stopMonitorRecording", "");
            executeJpcontrollerCommand("saveSnapshot "+ snapshotPath,"");


            exportProfilerSnapshot();
            System.out.println("Profiling cleanup/export completed.");
        } catch (Exception e) {
            System.err.println("Profiler cleanup failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Execute JPController command via CLI
     */
    private void executeJpcontrollerCommand(final String command,
            final String parameter) throws Exception {
        try {
            final List<String> cmdList = new ArrayList<>();
            cmdList.add(Constants.JPCONTROLLER_PATH);
            cmdList.add("localhost:" + JPROFILER_PORT); // Target the specific port
            cmdList.add("-n");
            cmdList.add(command);
            if (parameter != null && !parameter.isEmpty()) {
                cmdList.add(parameter);
            }

            System.out.println("Executing: " + cmdList);
            Process process = new ProcessBuilder(cmdList).inheritIO().start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                System.err.println("JPController command '" + command
                        + "' returned exit code: " + exitCode);

            }

        } catch (Exception e) {
            System.err.println("Error executing JPController command: " + command);
            throw e;
        }
    }

    /**
     * Step 6: Stop all profiling recordings via JPController
     */
    private void stopProfiling() throws Exception {
        System.out.println("Stopping all profiling recordings...");
        String outputDir = Constants.PROJECT_ROOT + "/" + JPROFILER_OUTPUT_DIR;
        String snapshotPath = outputDir + SNAPSHOT_FILE;

         try {
         executeJpcontrollerCommand("stopCPURecording", "");
         executeJpcontrollerCommand("stopAllocRecording", "");
         executeJpcontrollerCommand("stopMonitorRecording", "");
         executeJpcontrollerCommand("stopThreadProfiling", "");
         } catch (Exception e) {
         System.err.println("Error stopping profiling: " + e.getMessage());
         throw e;
         }

    }

    /**
     * Step 7: Save profiler snapshot
     */
    private void saveProfilerSnapshot() throws Exception {
        String outputDir = Constants.PROJECT_ROOT + File.separator + JPROFILER_OUTPUT_DIR;
        String snapshotPath = outputDir + SNAPSHOT_FILE;

        // Create output directory if it doesn't exist
        Files.createDirectories(Paths.get(outputDir));

        try {
            final List<String> cmdList = new ArrayList<>();
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

    /**
     * Step 8: Export profiler snapshot to CSVs
     */
    private void exportProfilerSnapshot() throws Exception {
        String outputDir = Constants.PROJECT_ROOT + "/" + JPROFILER_OUTPUT_DIR;
        String snapshotPath = outputDir + SNAPSHOT_FILE;

        try {
            final List<String> command = new ArrayList<>(Constants.JPEXPORT_COMMAND);

            System.out.println("Exporting snapshot to CSVs in: " + outputDir);
            Process process = new ProcessBuilder(command).inheritIO().start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                System.err.println("JPExport returned exit code: " + exitCode);
            }
            System.out.println("CSV export completed successfully");
        } catch (Exception e) {
            System.err.println("Error exporting snapshot to CSVs: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Phase 2: Joularjx profiling (similar pattern)
     */
    private long launchJoularjxPhase(final String aClasspath,
            final String aFQN, final String... programArgs) throws IOException {
        System.out.println("\n=== PHASE 2: Joularjx Profiling ===");

        final String javaPath = System.getProperty("java.home");
        final String spectraAgentPath = Constants.MY_AGENT_PATH;

        final List<String> command = new ArrayList<>();
        command.add("sudo");
        command.add("-S");
        command.add(javaPath + File.separator + "bin" + File.separator + "java");
        command.add("-Djoularjx.config=" + Constants.PROJECT_ROOT
                + "/src/test/resources/config.properties");
        command.add("-javaagent:" + spectraAgentPath);
        command.add("-cp");
        command.add(aClasspath);
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

    /**
     * Launch JPExport for converting profiler data
     */
    private Process launchJpexport() throws IOException {
        final List<String> command = new ArrayList<>(Constants.JPEXPORT_COMMAND);
        System.out.println("JPExport command: " + command);
        return new ProcessBuilder(command).inheritIO().start();
    }

    /**
     * Consume process output streams asynchronously
     */
    private void consumeProcessOutput(final Process process) {
        // Consume output stream
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
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
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()))) {
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

    static class JProfilerRemoteControllerClient {
        private final String host;
        private final int jmxPort;
        private MBeanServerConnection connection;
        private JMXConnector connector;
        private static final String CONTROLLER_MBEAN = "com.jprofiler:type=RemoteController";

        public JProfilerRemoteControllerClient(String host, int jmxPort) {
            this.host = host;
            this.jmxPort = jmxPort;
        }

        public void connect() throws Exception {
            String url = "service:jmx:rmi:///jndi/rmi://" + host + ":" + "8849" + "/jmxrmi";
            JMXServiceURL jmxUrl = new JMXServiceURL(url);
            connector = JMXConnectorFactory.connect(jmxUrl);
            connection = connector.getMBeanServerConnection();
            System.out.println("Connected to JProfiler RemoteController MBean on " + host + ":" + jmxPort);
        }

        public void startCPU() throws Exception {
            connection.invoke(new ObjectName(CONTROLLER_MBEAN),
                    "startCPURecording",
                    new Object[] { Boolean.TRUE },
                    new String[] { "boolean" });
        }

        public void stopCPU() throws Exception {
            connection.invoke(new ObjectName(CONTROLLER_MBEAN),
                    "stopCPURecording",
                    null,
                    null);
        }

        public void startAlloc() throws Exception {
            connection.invoke(new ObjectName(CONTROLLER_MBEAN),
                    "startAllocRecording",
                    new Object[] { Boolean.TRUE },
                    new String[] { "boolean" });
        }

        public void stopAlloc() throws Exception {
            connection.invoke(new ObjectName(CONTROLLER_MBEAN),
                    "stopAllocRecording",
                    null,
                    null);
        }

        public void startMonitor() throws Exception {
            connection.invoke(new ObjectName(CONTROLLER_MBEAN),
                    "startMonitorRecording",
                    null,
                    null);
        }

        public void stopMonitor() throws Exception {
            connection.invoke(new ObjectName(CONTROLLER_MBEAN),
                    "stopMonitorRecording",
                    null,
                    null);
        }

        public void stopThreadProfiling() throws Exception {
            connection.invoke(new ObjectName(CONTROLLER_MBEAN),
                    "stopThreadProfiling",
                    null,
                    null);
        }

        public void saveSnapshot(String path) throws Exception {
            connection.invoke(new ObjectName(CONTROLLER_MBEAN),
                    "saveSnapshot",
                    new Object[] { path },
                    new String[] { "java.lang.String" });
        }

        public void close() {
            try {
                connector.close();
            } catch (Exception ignored) {
            }
        }
    }
}
