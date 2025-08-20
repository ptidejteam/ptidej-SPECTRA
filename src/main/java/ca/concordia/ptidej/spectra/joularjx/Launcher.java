package ca.concordia.ptidej.spectra.joularjx;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

import ca.concordia.ptidej.spectra.analysis.CSVMerger;
import com.sun.jdi.Bootstrap;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.Connector;

import java.util.Map;

import com.sun.jdi.connect.LaunchingConnector;
import org.noureddine.joularjx.result.ResultWriter;

public class Launcher {
    private Process launchExternal(final ResultWriter aWriter,
                                   final String aClasspath, final String aFQN) throws IOException {

        Process process = this.launchJVMs(aClasspath, aFQN);
        return process; // This method is not used in the current implementation

    }

    private Process launchJProfiler(String javaPath, String jprofilerAgent, String classpath, String mainClass) throws IOException {


        LaunchingConnector launchingConnector = Bootstrap.virtualMachineManager().defaultConnector();
        Map<String, Connector.Argument> arguments = launchingConnector.defaultArguments();

        // Set main class and program arguments
        StringBuilder mainArg = new StringBuilder(mainClass);
        arguments.get("main").setValue(mainArg.toString());

        // Set options: agentpath and classpath
        StringBuilder options = new StringBuilder();
        if (jprofilerAgent != null && !jprofilerAgent.isEmpty()) {
            options.append("-agentpath:").append(jprofilerAgent)
                    .append("=port=8849,nowait," +
                            "config=/Users/mac/Desktop/jprofiler_config.xml ");

        }
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
            VirtualMachine vm = launchingConnector.launch(arguments);
            System.out.println("Launched JVM with PID: " + vm.process().pid());
            Thread.sleep(2000);

            Process process = vm.process();
            Process jpcontroller = null;
            try {
                jpcontroller = new ProcessBuilder(
                        "/Applications/JProfiler" +
                                ".app/Contents/Resources/app/bin/jpcontroller",
                        "-n", "-f",
                        "output/jprofiler/command.txt").inheritIO().start();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            if (process != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                    }
                }
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String errorLine;
                    while ((errorLine = errorReader.readLine()) != null) {
                        System.err.println(errorLine);
                    }
                }
                int exitCode = process.waitFor();
                System.out.println("Process exited with code: " + exitCode);
            } else {
                System.out.println("No local process handle available (remote or unsupported connector).");
            }
            return vm.process();
        } catch (Exception e) {
            throw new IOException("Failed to launch JVM with LaunchingConnector", e);
        }


    }

    // 2. Launch Jpexport for profiling data
    private Process launchJpexport() throws IOException {
        List<String> command = List.of(
                "/Applications/JProfiler.app/Contents/Resources/app/bin/jpexport",
                "output/jprofiler/snapshot.jps", "AllObjects", "-format=csv",
                "output/Jprofiler/allobjects.csv", "CallTree", "-format=xml",
                "-aggregation=method", "output/Jprofiler/calltree.csv.xml",
                "Hotspots", "-format=csv", "output/Jprofiler/hotspots.csv"
        );
        return new ProcessBuilder(command).inheritIO().start();
    }

    // 3. Launch JVM with Joularjx and Spectra agent
    private Process launchJoularjx(String javaPath, String joularjxPath, String spectraAgentPath, String classpath, String mainClass) throws IOException {

        List<String> command = new ArrayList<>();
        command.add("sudo");
        command.add("-S");
        command.add(javaPath + "/bin"+"/java");
        command.add("-javaagent:" + spectraAgentPath);
        command.add("-cp");
        command.add(joularjxPath + File.pathSeparator + classpath);
        command.add(mainClass);
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

        return process;

    }

    private Process launchJVMs(final String aClasspath, final String aFQN) throws IOException {


        // Path to the Java executable
        final String javaPath = System.getProperty("java.home"); // "../../../Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home/bin/java"
        // agent JAR path
        final String myAgentPath = "target/Spectra-with-dependencies.jar";
        final String JoularjxPath = "../../../Downloads/joularjx/joularjx/target" +
                "/joularjx-3.0.1" +
                ".jar";

        final String jprofilerAgent = "/Applications/JProfiler" +
                ".app/Contents/Resources/app/bin/macos/libjprofilerti.jnilib";


        // 1. Launch JVM with JProfiler agent
        Process jprofilerProcess = launchJProfiler(javaPath, jprofilerAgent, aClasspath, aFQN);
        long pid = jprofilerProcess.pid();

       //  2. Launch Jpexport for profiling data
        System.out.println("Launching Jpexport...");
        Process jpexport = launchJpexport();
        try {
            jpexport.waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        // 3. Launch JVM with Joularjx and Spectra agent
        System.out.println("Launching Joularjx with Spectra agent...");
        String aClasspathWithJoularjx = JoularjxPath + File.pathSeparator + aClasspath;
        Process joularProcess = launchJoularjx(javaPath, JoularjxPath, myAgentPath,
                aClasspath, aFQN);

        System.out.println("Launched all JVMs and tools.");

        return joularProcess; // Return the process for further handling if needed

    }

    public void launch(final ResultWriter aWriter, final String aClasspath, final String aFQN) throws IOException {

        final Process process = this.launchExternal(aWriter, aClasspath, aFQN);

        if (process.exitValue() == 0) {
			CSVMerger.runCSVMerger();
		}


    }

    private void enterPassword(Process process) throws IOException {
        try (OutputStream os = process.getOutputStream()) {
            os.write("1234".getBytes());
            os.flush();
        }
    }
    //	public static Process launchInternal(final ResultWriter aWriter,
    //			final String classpath, final String aFQN) throws IOException {
    //
    //		String agentJarPath = System.getProperty("user.dir")
    //				+ "/target/Spectra-with-dependencies.jar";
    //		String joularjxPath = System.getProperty("user.home")
    //				+ "/Downloads/joularjx-3.0.1.jar";
    //
    //		LaunchingConnector connector = Bootstrap.virtualMachineManager()
    //				.defaultConnector();
    //		Map<String, Connector.Argument> arguments = connector
    //				.defaultArguments();
    //
    //		// Set the main class to run
    //		arguments.get("main").setValue(aFQN);
    //
    //		String VMOptions = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,"
    //				+ "address=*:5005";
    //		String javaAgent = "-javaagent:" + agentJarPath;
    //		String fullClasspath = "-cp " + joularjxPath + File.pathSeparator
    //				+ classpath;
    //
    //		String finalOptions = String.join(" ", VMOptions, javaAgent,
    //				fullClasspath);
    //
    //		// Set JVM options
    //		arguments.get("options").setValue(finalOptions);
    //
    //		try {
    //			return connector.launch(arguments).process();
    //		}
    //		catch (VMStartException | IllegalConnectorArgumentsException e) {
    //			throw new RuntimeException("VM failed to start", e);
    //
    //		}
    //	}

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
////        options.append("-Djdk.attach.allowAttachSelf=true");
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



