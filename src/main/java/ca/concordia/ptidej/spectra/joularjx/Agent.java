
package ca.concordia.ptidej.spectra.joularjx;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.noureddine.joularjx.result.CsvResultWriter;
import org.noureddine.joularjx.cpu.Cpu;
import org.noureddine.joularjx.cpu.CpuFactory;
import org.noureddine.joularjx.monitor.MonitoringStatus;
import org.noureddine.joularjx.result.ResultWriter;
import org.noureddine.joularjx.utils.AgentProperties;
import org.noureddine.joularjx.utils.JoularJXLogging;


import com.sun.management.OperatingSystemMXBean;

public class Agent {

    public static final String NAME_THREAD_NAME = "JoularJX Agent Thread";
    public static final String COMPUTATION_THREAD_NAME = "JoularJX Agent Computation";
    private static final Logger logger = JoularJXLogging.getLogger();

    /**
     * JVM hook to statically load the java agent at startup. After the Java Virtual
     * Machine (JVM) has initialized, the premain method will be called. Then the
     * real application main method will be called.
     */
    public static void premain(String args, Instrumentation inst) {
        Thread.currentThread().setName(NAME_THREAD_NAME);
        AgentProperties properties = new AgentProperties();
        JoularJXLogging.updateLevel(properties.getLoggerLevel());

        logger.info("+---------------------------------+");
        logger.info("| Spectra-JoularJX Agent Version 3.0.2   |");
        logger.info("+---------------------------------+");

        ThreadMXBean threadBean = createThreadBean();

        // Get Process ID of current application
        long appPid = ProcessHandle.current().pid();

        // Creating the required folders to store the result files generated later on
        ResultTreeManager resultTreeManager = new ResultTreeManager(properties, 123,
                123456789);
        if (!resultTreeManager.create()) {
            logger.log(Level.WARNING,
                    "Error(s) occurred while creating the result folder hierarchy. Some results may not be reported.");
        }

        Cpu cpu = CpuFactory.getCpu(properties);

        OperatingSystemMXBean osBean = createOperatingSystemBean(cpu);
        MonitoringStatus status = new MonitoringStatus();

//		final ResultWriter writer = new ResultWriter() {
//			@Override
//			public void write(final String methodName, final double methodPower) {
//				System.out.println(methodName + " : " + methodPower);
//			}
//
//			@Override
//			public void setTarget(String name, boolean overwrite) throws IOException {
//				// throw new RuntimeException("Boom!");
//				//Thread.dumpStack();
//
//			}
//
//			@Override
//			public void closeTarget() throws IOException {
//				//Thread.dumpStack();
//			}
//		};

        ResultWriter writer = new CsvResultWriter();
        MonitoringHandler monitoringHandler = new MonitoringHandler(appPid, properties, writer, cpu, status, osBean,
                threadBean, resultTreeManager);
        ShutdownHandler shutdownHandler = new ShutdownHandler(appPid, writer, cpu, status, properties,
                resultTreeManager);

        logger.log(Level.INFO, "Initialization finished");

        new Thread(monitoringHandler, COMPUTATION_THREAD_NAME).start();
        Runtime.getRuntime().addShutdownHook(new Thread(shutdownHandler));

    }

    /**
     * Creates and returns a ThreadMXBean. Checks if the Thread CPU Time is
     * supported by the JVM and enables it if it is disabled.
     */
    private static ThreadMXBean createThreadBean() {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        // Check if CPU Time measurement is supported by the JVM. Quit otherwise
        if (!threadBean.isThreadCpuTimeSupported()) {
            logger.log(Level.SEVERE, "Thread CPU Time is not supported on this Java Virtual Machine. Existing...");
            System.exit(1);
        }

        // Enable CPU Time measurement if it is disabled
        if (!threadBean.isThreadCpuTimeEnabled()) {
            threadBean.setThreadCpuTimeEnabled(true);
        }

        return threadBean;
    }

    /**
     * Creates and returns an OperatingSystemMXBean, used to collect CPU and process
     * loads.
     *
     * @param cpu a {@link Cpu} implementation
     * @return an OperatingSystemMXBean
     */
    private static OperatingSystemMXBean createOperatingSystemBean(Cpu cpu) {
        // Get OS MxBean to collect CPU and Process loads
        OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

        // Loop for a couple of seconds to initialize OSMXBean to get accurate details
        // (first call will return -1)
        logger.log(Level.INFO, "Please wait while initializing JoularJX...");
        for (int i = 0; i < 2; i++) {
            osBean.getCpuLoad(); // In future when Java 17 becomes widely deployed, use getCpuLoad() instead
            osBean.getProcessCpuLoad();

            cpu.initialize();

            try {
                Thread.sleep(500);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        return osBean;
    }

    /**
     * Private constructor
     */
    private Agent() {
    }

}
