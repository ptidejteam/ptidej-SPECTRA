package ca.concordia.ptidej.spectra.ChildJVM;

import ca.concordia.ptidej.spectra.Profile.MonitoringHandler;
import ca.concordia.ptidej.spectra.Profile.ResultTreeManager;
import ca.concordia.ptidej.spectra.Profile.ShutdownHandler;
import com.sun.management.OperatingSystemMXBean;
import org.noureddine.joularjx.cpu.Cpu;
import org.noureddine.joularjx.cpu.CpuFactory;
import org.noureddine.joularjx.monitor.MonitoringStatus;
import org.noureddine.joularjx.result.CsvResultWriter;
import org.noureddine.joularjx.result.ResultWriter;
import org.noureddine.joularjx.utils.AgentProperties;
import org.noureddine.joularjx.utils.JoularJXLogging;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JoularJXMain {
    private static final String REAL_MAIN_PROPERTY = "spectra.realMain";

    public static final String NAME_THREAD_NAME = "JoularJX Agent Thread";
    public static final String COMPUTATION_THREAD_NAME = "JoularJX Agent Computation";
    private static final Logger logger = JoularJXLogging.getLogger();

    public static void main(String[] args) throws Exception {



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
