package ca.concordia.ptidej.spectra.joularjx;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.VMStartException;
import org.noureddine.joularjx.result.ResultWriter;

import com.sun.jdi.Bootstrap;
import com.sun.jdi.VirtualMachine;
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


        // Set main class and program arguments
        final StringBuilder mainArg = new StringBuilder(mainClass);
        if (programArgs != null) {
            for (String arg : programArgs) {
                mainArg.append(" ").append(arg);
            }
        }
        System.out.println("Classpath:" + classpath);
        arguments.get("main").setValue(mainArg.toString());

        // Set options: agentpath and classpath
        final StringBuilder options = new StringBuilder();
        if (jprofilerAgent != null && !jprofilerAgent.isEmpty()) {
            options.append("-agentpath:").append(jprofilerAgent)
                    .append("=port=8849,nowait,config=src/main/resources/jprofiler_config.xml ");

        }
//        options.append("-javaagent:")
//                .append(Constants.MY_AGENT_PATH)
//                .append(" ");

        if (classpath != null && !classpath.isEmpty()) {
            options.append("-cp ").append(classpath).append(" ");
        }
        options.append("-Djdk.attach.allowAttachSelf=true");
        arguments.get("options").setValue(options.toString().trim());
        arguments.get("suspend").setValue("false");

        System.out.println("Command: " + arguments);
        // Set java executable path if needed (optional)
        if (javaPath != null && !javaPath.isEmpty()) {
            arguments.get("home").setValue(javaPath);
        }

        try {
            final VirtualMachine vm = launchingConnector.launch(arguments);
            System.out.println("Launched JVM with PID: " + vm.process().pid());
            Thread.sleep(2000);

            final Process process = vm.process();
            Process jpcontroller = null;
            try {
                jpcontroller = new ProcessBuilder(Constants.JPCONTROLLER_PATH,
                        "-n", "-f", "output/jprofiler/command.txt").inheritIO()
                        .start();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            if (process != null) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                    }
                }
                try (BufferedReader errorReader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream()))) {
                    String errorLine;
                    while ((errorLine = errorReader.readLine()) != null) {
                        System.err.println(errorLine);
                    }
                }
                int exitCode = process.waitFor();
                System.out.println("Process exited with code: " + exitCode);
            } else {
                System.out.println(
                        "No local process handle available (remote or unsupported connector).");
            }
            return vm.process().pid();
        } catch (Exception e) {
            throw new IOException(
                    "Failed to launch JVM with LaunchingConnector", e);
        }

    }

    // 2. Launch Jpexport for profiling data
    private Process launchJpexport() throws IOException {
        final List<String> command = Constants.JPEXPORT_COMMAND;
        return new ProcessBuilder(command).inheritIO().start();
    }

    //	 3. Launch JVM with Joularjx and Spectra agent
    private long launchJoularjx(final String javaPath, final String joularjxPath,
                                final String spectraAgentPath,final String classpath,
                                final String mainClass, final String... programArgs)
            throws IOException {

        final List<String> command = new ArrayList<>();
        command.add("sudo");
        command.add("-S");
        command.add(javaPath + "/bin" + "/java");
        //command.add("-Djoularjx.config=src/test/resources/config.properties");
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

        // Start the process
        final Process process = processBuilder.start();

        // Provide the sudo password
        enterPassword(process);

        // Consume the output stream
        try (final BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("Output: " + line);
            }
        }

        try (final BufferedReader errorReader = new BufferedReader(
                new InputStreamReader(process.getErrorStream()))) {
            String errorLine;
            while ((errorLine = errorReader.readLine()) != null) {
                System.err.println("Error: " + errorLine);
            }
        }

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

    public final class Constants {
        public static final String JOULARJX_PATH = "src/main/resources/joularjx-3.0.1.jar";
        public static final String JPROFILER_AGENT = "/Applications/JProfiler.app/Contents/Resources/app/bin/macos/libjprofilerti.jnilib";
        public static final String MY_AGENT_PATH = "target/Spectra-with-dependencies.jar";
        public static final String JPCONTROLLER_PATH = "/Applications/JProfiler.app/Contents/Resources/app/bin/jpcontroller";
        public static final List<String> JPEXPORT_COMMAND = List.of(
                "/Applications/JProfiler.app/Contents/Resources/app/bin/jpexport",
                "output/jprofiler/snapshot.jps", "AllObjects", "-format=csv",
                "output/Jprofiler/allobjects.csv", "CallTree", "-format=xml",
                "-aggregation=method", "output/Jprofiler/calltree.csv.xml",
                "Hotspots", "-format=csv", "output/Jprofiler/hotspots.csv");

    }

}

// // 3. Launch JVM with Joularjx and Spectra agent
//    private Process launchJoularjx(String javaPath, String joularjxPath, String spectraAgentPath, String classpath, String mainClass) throws IOException {
//        LaunchingConnector launchingConnector = Bootstrap.virtualMachineManager().defaultConnector();
//        Map<String, Connector.Argument> arguments = launchingConnector.defaultArguments();
//
//        // Build main class and arguments
//            arguments.get("main").setValue(mainClass);
//
//            // Build options for agents and classpath
//            StringBuilder options = new StringBuilder();
//            if (spectraAgentPath != null && !spectraAgentPath.isEmpty()) {
//                options.append("-javaagent:").append(spectraAgentPath).append(" ");
//                options.append("-cp ");
//
//            }
//
//            if (joularjxPath != null && !joularjxPath.isEmpty()) {
//                options.append(joularjxPath).append(" "+File.pathSeparator+" " +classpath).append(" ");
//            }
//
//        options.append("-Djdk.attach.allowAttachSelf=true");
//            arguments.get("options").setValue(options.toString().trim());
//        arguments.get("suspend").setValue("false");
//
//        System.out.println("Command: "+arguments);
//            // Set java home if needed (optional)
//            if (javaPath != null && !javaPath.isEmpty()) {
//                arguments.get("home").setValue(javaPath);
//            }
//
//            try {
//                VirtualMachine vm = launchingConnector.launch(arguments);
//
//                // Optionally: capture output (works for local launch)
//                Process process = vm.process();
//                if (process != null) {
//                    new Thread(() -> {
//                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
//                            String line;
//                            while ((line = reader.readLine()) != null) {
//                                System.out.println("Output: " + line);
//                            }
//                        } catch (IOException e) {
//                            e.printStackTrace();
//                        }
//                    }).start();
//
//                    new Thread(() -> {
//                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
//                            String line;
//                            while ((line = reader.readLine()) != null) {
//                                System.err.println("Error: " + line);
//                            }
//                        } catch (IOException e) {
//                            e.printStackTrace();
//                        }
//                    }).start();
//                }
//
//                return vm.process();
//            } catch (Exception e) {
//                throw new IOException("Failed to launch JVM with LaunchingConnector", e);
//            }
//
//
//    }

