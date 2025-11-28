package ca.concordia.ptidej.spectra.ChildJVM;

import com.jprofiler.api.controller.Controller;

public class SpectraMain {

    private static final String REAL_MAIN_PROPERTY = "spectra.realMain";

    public static void main(String[] args) throws Exception {
        System.out.println("[Spectra] Starting JProfiler recordings...");
        Controller.startCPURecording(true);      // true = reset existing data
        Controller.startAllocRecording(true);
        Controller.startMonitorRecording();
        Controller.startThreadProfiling();
        System.out.println("[Spectra] All recordings started");
        // 1) Register shutdown hook in CHILD JVM
        Runtime.getRuntime().addShutdownHook(new SpectraShutdownJVMThread());
        System.out.println("[Spectra] Shutdown hook registered in child JVM.");

        // 2) Resolve the real main class to delegate to
        String realMain = System.getProperty(REAL_MAIN_PROPERTY);
        if (realMain == null || realMain.isEmpty()) {
            throw new IllegalStateException(
                    "System property '" + REAL_MAIN_PROPERTY + "' not set. " +
                            "Pass -Dspectra.realMain=<fully.qualified.MainClass> to the child JVM."
            );
        }
        System.out.println("[Spectra] Delegating to real main: " + realMain);

        Class<?> realClass = Class.forName(realMain);
        realClass.getMethod("main", String[].class).invoke(null, (Object) args);
    }
}
