package ca.concordia.ptidej.spectra.ChildJVM;

import com.jprofiler.api.controller.Controller;

public class SpectraMain {

    private static final String REAL_MAIN_PROPERTY = "spectra.realMain";

    public static void main(String[] args) throws Exception {

        String realMain = System.getProperty(REAL_MAIN_PROPERTY);
        if (realMain == null || realMain.isEmpty()) {
            throw new IllegalStateException(
                    "System property '" + REAL_MAIN_PROPERTY + "' not set. " +
                            "Pass -Dspectra.realMain=<fully.qualified.MainClass> to the child JVM."
            );
        }
        System.out.println("[Spectra Main] Delegating to real main: " + realMain);

        Class<?> realClass = Class.forName(realMain);
        realClass.getMethod("main", String[].class).invoke(null, (Object) args);
    }
}
