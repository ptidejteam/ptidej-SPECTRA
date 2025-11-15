package ca.concordia.ptidej.spectra.Profile;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.noureddine.joularjx.result.ResultWriter;

import com.sun.jdi.Bootstrap;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;

import ca.concordia.ptidej.spectra.analysis.CSVMerger;

public class Launcher {

    public void launch(final ResultWriter aWriter, final String aClasspath,
                       final String aFQN, final String... programArgs) throws IOException {

        final long pid = this.launchExternal(aWriter, aClasspath, aFQN, programArgs);
        if (pid > 0) {
            CSVMerger.runCSVMerger(Arrays.toString(programArgs));
        }

    }

    private long launchExternal(final ResultWriter aWriter,
                                final String aClasspath, final String aFQN,
                                final String... programArgs) throws IOException {

        return this.launchJVMs(aClasspath, aFQN, programArgs);


    }

    private long launchJVMs(final String aClasspath, final String aFQN,
                            final String... programArgs)
            throws IOException {

        // Path to the Java executable
        final String javaPath = System.getProperty("java.home");
        final String myAgentPath = Constants.MY_AGENT_PATH;

        final String jprofilerAgent = Constants.JPROFILER_AGENT;

        // 1. Launch JVM with JProfiler agent
        final long jprofilerProcessId = this.launchJProfiler(javaPath,
                jprofilerAgent, aClasspath, aFQN, programArgs);
        if (jprofilerProcessId > 0) {
            System.out.println("Finished Executing JProfiler with PID: " + jprofilerProcessId);
        } else {
            throw new RuntimeException("Failed to launch JProfiler.");
        }
        //2. Launch Jpexport for profiling data
        System.out.println("Launching Jpexport...");
        Process jpexport = launchJpexport();
        try {
            jpexport.waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // 3. Launch JVM with Joularjx and Spectra agent
        System.out.println("Launching Joularjx with Spectra agent...");

        String aClasspathWithJoularjx = Constants.JOULARJX_PATH
                + File.pathSeparator + aClasspath;
        long joularProcessid = launchJoularjx(javaPath, Constants.JOULARJX_PATH,
                myAgentPath, aClasspath, aFQN, programArgs);

        if (joularProcessid > 0) {
            System.out.println(
                    "Finished Executing Joular with PID: " + joularProcessid);
            System.out.println("Launched all JVMs and tools.");

        } else {
            throw new RuntimeException("Failed to launch Joular.");
        }

        return joularProcessid;

    }

    private long launchJProfiler(final String javaPath, final String jprofilerAgent,
                                 final String classpath, final String mainClass,
                                 final String... programArgs) throws IOException {

        final LaunchingConnector launchingConnector = Bootstrap
                .virtualMachineManager().defaultConnector();
        final Map<String, Connector.Argument> arguments = launchingConnector
                .defaultArguments();

        final List<String> command = new ArrayList<>();
        command.add(javaPath + "/bin" + "/java");
        command.add("-agentpath:" + jprofilerAgent + "=port=8849,nowait,config=" + Constants.PROJECT_ROOT + "/src/main/resources/jprofiler_config.xml");
        command.add("-cp");
        command.add(classpath);
        command.add("-Djdk.attach.allowAttachSelf=true");
        command.add(mainClass);
        if (programArgs != null) {
            for (String arg : programArgs) {
                command.add(arg);
            }
        }
        System.out.println("Classpath:" + classpath);

        System.out.println("Command2 " + command);

        final ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        // Change Current Working Directory
        File newCurrentWorkingDirectory = new File("../Ptidej/ptidej-Ptidej/POM/");
        processBuilder.directory(newCurrentWorkingDirectory);
        // Start the process
        final Process processJProfiler = processBuilder.start();

        // Provide the sudo password
        enterPassword(processJProfiler);
        System.out.println("Launched JVM with PID: " + processJProfiler.pid());

        try {
            Thread.sleep(5000); // Wait for the JVM to start and be ready for profiling
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }


        Process jpcontroller = null;
        try {
            jpcontroller = new ProcessBuilder(Constants.JPCONTROLLER_PATH,
                    "-n", "-f", Constants.PROJECT_ROOT + "/Output/JProfiler/command.txt").inheritIO().start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (processJProfiler != null) {
            // Async stream - Consume the output stream
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(processJProfiler.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();

            // Async stream - Consume the error stream
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(processJProfiler.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.err.println( line);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();
            int exitCode = 0;
            try {
                exitCode = processJProfiler.waitFor();
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
            System.out.println("Process exited with code: " + exitCode);
        } else {
            System.out.println(
                    "No local process handle available (remote or unsupported connector).");
        }
        return processJProfiler.pid();


    }

    // 2. Launch Jpexport for profiling data
    private Process launchJpexport() throws IOException {
        final List<String> command = Constants.JPEXPORT_COMMAND;
        return new ProcessBuilder(command).inheritIO().start();
    }

    //	 3. Launch JVM with Joularjx and Spectra agent
    private long launchJoularjx(final String javaPath, final String joularjxPath,
                                final String spectraAgentPath, final String classpath,
                                final String mainClass, final String... programArgs)
            throws IOException {

        final List<String> command = new ArrayList<>();
        command.add("sudo");
        command.add("-S");
        command.add(javaPath + "/bin" + "/java");
        command.add("-Djoularjx.config=" + Constants.PROJECT_ROOT + "/src/test/resources/config.properties");
        command.add("-javaagent:" + spectraAgentPath);
        command.add("-cp");
        command.add(/**joularjxPath + "=include=*,exclude=-XX:-Inline" + File.pathSeparator + */classpath);
        command.add(mainClass);
        if (programArgs != null) {
            for (String arg : programArgs) {
                command.add(arg);
            }
        }
        System.out.println("Classpath:" + classpath);

        System.out.println("Command2 " + command);

        final ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        // Change Current Working Directory
        File newCurrentWorkingDirectory = new File("../Ptidej/ptidej-Ptidej/POM");
        processBuilder.directory(newCurrentWorkingDirectory);
        // Start the process
        final Process process = processBuilder.start();

        // Provide the sudo password
        enterPassword(process);



        // Async stream - Consume the output stream
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        // Async stream - Consume the error stream
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.err.println( line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();


        try {
            int exitCode = process.waitFor();
            System.out.println("Process exited with code: " + exitCode);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Process was interrupted", e);
        }

        return process.pid();

    }


    private void enterPassword(Process process) throws IOException {
        try (OutputStream os = process.getOutputStream()) {
            os.write("1234".getBytes());
            os.flush();
        }
    }
}
