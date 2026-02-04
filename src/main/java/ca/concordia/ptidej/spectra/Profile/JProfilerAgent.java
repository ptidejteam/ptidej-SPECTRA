package ca.concordia.ptidej.spectra.Profile;

import ca.concordia.ptidej.spectra.ChildJVM.SpectraShutdownJVMThread;
import com.jprofiler.api.controller.Controller;
import com.sun.jdi.VirtualMachine;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.List;

import static ca.concordia.ptidej.spectra.Profile.Launcher.waitForPortAvailable;

public class JProfilerAgent {
    public static void premain(String args, Instrumentation inst) throws Exception {
        System.out.println("[Spectra JProfiler Agent] Premain");

//        long id = ProcessHandle.current().pid();
//        try {
//            System.out.println("[Spectra JProfiler Agent] tried to connect");
//
//            String java_home = System.getProperty("java.home");
//
//            ProcessBuilder processBuilder = new ProcessBuilder()
//                    .inheritIO()
//                    .command(
//                    List.of(
//                            Constants.JPROFILER_BIN_PATH + "/jpenable",
//                            "--noinput",
//                            "--offline",
//                            "--pid=" + id,
//                            "--config=" + Constants.SPECTRA_ROOT + "/src/main/resources/jprofiler_config.xml",
//                            "--id=" + "110"
//                    ));
//            processBuilder.environment().put("LD_LIBRARY_PATH", Constants.JPROFILER_BIN_PATH + "/linux-x64");
//            final Process process = processBuilder.start();
//            int exitCode = process.waitFor();
//            System.out.printf("[Spectra JProfiler Agent] %d\n", exitCode);
//
//
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }

        Runtime.getRuntime().addShutdownHook(new SpectraShutdownJVMThread());
        System.out.println("[Spectra JProfiler Agent] Shutdown hook registered in child JVM.");

        Controller.startCPURecording(true);
        Controller.startAllocRecording(true);
        Controller.startThreadProfiling();
        Controller.startMonitorRecording();

       // System.out.println("[Spectra JProfiler Agent] Recording should be started.");

//        try {
//            Thread.sleep(10000);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }

    }
}
