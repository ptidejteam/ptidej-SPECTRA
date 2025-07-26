package ca.concordia.ptidej.spectra.joularjx;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.sun.jdi.Bootstrap;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;
import org.noureddine.joularjx.result.ResultWriter;

import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.VMStartException;


public class Launcher {
    private Process launchExternal(final ResultWriter aWriter, final String aClasspath, final String aFQN)
            throws IOException {

        // Path to the Java executable
        final String javaPath = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        ;
        //java -javaagent:target/joularjx-with-dependencies.jar -cp target/classes org.noureddine.joularjx.OverloadTest
        // agent JAR path
        final String myAgentPath = System.getProperty("user.dir") + "/target/Spectra-with-dependencies.jar";
        final String JoularjxPath = "~/joularjx/joularjx/target/joularjx-3.0.1.jar";
        // Build the command to start the JVM
        List<String> command = new ArrayList<>();
        command.add("sudo");
        command.add("-S");
        command.add(javaPath);
        command.add("-javaagent:" + myAgentPath);
        command.add("-DresultWriterTarget=Output/Joularjx/");
        // command.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=8000");
        command.add("-cp");
        command.add(JoularjxPath + File.pathSeparator + aClasspath);
        command.add(aFQN);

        // Create the ProcessBuilder
        final ProcessBuilder processBuilder = new ProcessBuilder(command);

        processBuilder.redirectErrorStream(true);

        // Set the working directory (optional, adjust as needed)
        processBuilder.directory(new File(System.getProperty("user.dir")));

        System.out.println("Launching JVM with:");
        System.out.println("Main Class : " + aFQN);
        System.out.println("Classpath  : " + aClasspath);
        System.out.println("Agent Path : " + myAgentPath);
        System.out.println("Command    : " + String.join(" ", command)); //Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home/bin/java -javaagent:/Users/mac/Documents/RA/SPECTRA/target/Spectra-with-dependencies.jar
// -DresultWriterTarget=Output/Joularjx/ -cp
// ~/joularjx/joularjx/target/joularjx-3.0.1.jar:target/test-classes
// ca.concordia.ptidej.spectra.example.OverloadTest


        // Start the process
        final Process process = processBuilder.start();

        return process;
    }

    public static Process launchAPI(final ResultWriter writer,
                                    final String aClasspath,
                                    final String aFQN) throws IOException
            , IllegalConnectorArgumentsException {
        String agentJarPath = System.getProperty("user.dir") + "/target/Spectra-with-dependencies.jar";
        LaunchingConnector launchingConnector = Bootstrap.virtualMachineManager().defaultConnector();
        Map<String, Connector.Argument> arguments = launchingConnector.defaultArguments();

        arguments.get("main").setValue(aFQN);

        // Construct VM options
        String vmOptions = "-javaagent:" + agentJarPath +
                " -DresultWriterTarget=" + "Output/Joularjx" +
                " -cp " + "~/joularjx/joularjx/target/joularjx-3.0.1.jar:" + aClasspath;
        arguments.get("options").setValue(vmOptions);

        // It forms /Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home/bin/java
        // -javaagent:/Users/mac/Documents/RA/SPECTRA/target/Spectra-with-dependencies.jar -DresultWriterTarget=Output/Joularjx/
        // -cp target/test-classes -Xdebug -Xrunjdwp:transport=dt_socket,address=localhost:53387,suspend=y,includevirtualthreads=n ca.concordia.ptidej.spectra.example.OverloadTest


        arguments.get("suspend").setValue("false");
        arguments.remove("launch");

        // Launch the VM
        VirtualMachine vm = null;
        try {
            vm = launchingConnector.launch(arguments);
        } catch (VMStartException e) {
            throw new RuntimeException(e);
        }

        // Return the underlying process
        return vm.process();


    }


    public void launch(final ResultWriter aWriter, final String aClasspath, final String aFQN) throws IOException {
        final Process processExternal = this.launchExternal(aWriter,
                aClasspath, aFQN);
        Process processAPI = null;
        try {
            processAPI = this.launchAPI(aWriter, aClasspath, aFQN);
        } catch (IllegalConnectorArgumentsException e) {
            System.out.println(e);
        }


        // Provide the sudo password
        enterPassword(processExternal);

        // Consume the output stream
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(processExternal.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("Output: " + line);
            }
        }

        try (BufferedReader errorReader =
                     new BufferedReader(new InputStreamReader(processExternal.getErrorStream()))) {
            String errorLine;
            while ((errorLine = errorReader.readLine()) != null) {
                System.err.println("Error: " + errorLine);
            }
        }

        try {
            int exitCode = processExternal.waitFor();
            System.out.println("Process exited with code: " + exitCode);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Process was interrupted", e);
        }
    }

    private void enterPassword(Process process) throws IOException {
        try (OutputStream os = process.getOutputStream()) {
            os.write("1234".getBytes());
            os.flush();
        }
    }
}
