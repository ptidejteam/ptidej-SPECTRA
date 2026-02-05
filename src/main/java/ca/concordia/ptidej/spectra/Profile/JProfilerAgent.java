package ca.concordia.ptidej.spectra.Profile;

import ca.concordia.ptidej.spectra.ChildJVM.SpectraShutdownJVMThread;
import com.jprofiler.api.controller.Controller;
import java.lang.instrument.Instrumentation;

public class JProfilerAgent {
    public static void premain(String args, Instrumentation inst) throws Exception {
        System.out.println("[Spectra JProfiler Agent] Premain");

        Runtime.getRuntime().addShutdownHook(new SpectraShutdownJVMThread());
        System.out.println("[Spectra JProfiler Agent] Shutdown hook registered in child JVM.");

        Controller.startCPURecording(true);
        Controller.startAllocRecording(true);
        Controller.startThreadProfiling();
        Controller.startMonitorRecording();
        System.out.println("[Spectra JProfiler Agent] Recording Started.");
    }
}
