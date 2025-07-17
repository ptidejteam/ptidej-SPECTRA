//package ca.concordia.ptidej.spectra;
//
//import ca.concordia.ptidej.spectra.example.OverloadTest;
//import com.sun.management.OperatingSystemMXBean;
//import org.junit.jupiter.api.Test;
//import org.noureddine.joularjx.cpu.Cpu;
//import org.noureddine.joularjx.monitor.MonitoringStatus;
//import org.noureddine.joularjx.result.ResultTreeManager;
//import org.noureddine.joularjx.result.ResultWriter;
//import org.noureddine.joularjx.utils.AgentProperties;
//
//import java.lang.management.ManagementFactory;
//import java.lang.management.ThreadMXBean;
//import java.util.Map;
//import java.util.concurrent.atomic.AtomicBoolean;
//
//import static ca.concordia.ptidej.spectra.joularjx.MonitoringHandler.*;
//import static org.junit.jupiter.api.Assertions.*;
//
//public class ResolveTest {
//    @Test
//    public void testCorrectMethod() {
//        String resolved = resolve("org.noureddine.joularjx.monitor.MonitoringHandler"
//                , "destroyingVM", 459);
//        assertEquals("org.noureddine.joularjx.monitor.MonitoringHandler.destroyingVM()", resolved);
//    }
//    @Test
//    public void testCorrectMethod2() {
//        String resolved = resolve("org.noureddine.joularjx.monitor.MonitoringHandler"
//                , "destroyingVM", 467);
//        System.out.println(resolved);
//        assertEquals("org.noureddine.joularjx.monitor.MonitoringHandler.destroyingVM(double)", resolved);
//    }
//    @Test
//    public void testCorrectMethod3() {
//        String resolved = resolve("org.noureddine.joularjx.monitor.MonitoringHandler"
//                , "destroyingVM", 475);
//        System.out.println(resolved);
//        assertEquals("org.noureddine.joularjx.monitor.MonitoringHandler.destroyingVM(String)", resolved);
//    }
//
//
//    // Create a VM, called VM2
//    // Connect the output stream of VM2 into the input stream of VM1
//    // Run JoularX in VM2
//    // Apply JoularX on OverloadTest in VM2
//    // Parse the output stream of VM2 from inside VM1 to get the energy data.
//    @Test
//    public void testEnergyOfAllOverloadedMethods() throws Exception {
//        // Create a flag to stop the monitoring thread
//        AtomicBoolean stopMonitoring = new AtomicBoolean(false);
//
//        AgentProperties props = new AgentProperties() {
//            @Override
//            public boolean filtersMethod(String methodName) {
//                return methodName != null && methodName.contains("add");
//            }
//        };
//
//        MonitoringStatus status = new MonitoringStatus();
//        ResultWriter writer = new ResultWriter() {
//            public void setTarget(String path, boolean overwrite) {}
//            public void write(String key, double value) {}
//            public void closeTarget() {}
//        };
//
//        Cpu cpu = new Cpu() {
//            public void close() {}
//            public void initialize() {}
//            public double getInitialPower() { return 0.0; }
//            public double getCurrentPower(double cpuLoad) { return 1.0; }
//            public double getMaxPower(double cpuLoad) { return 10.0; }
//        };
//
//        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
//        OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
//        ResultTreeManager treeManager = new ResultTreeManager(props, 1234L, System.currentTimeMillis());
//
//        MonitoringHandler handler = new MonitoringHandler(1234L, props, writer, cpu, status, osBean, threadMXBean, treeManager);
//
//        Thread monitorThread = new Thread(() -> {
//            try {
//                while (!stopMonitoring.get()) {
//                    handler.run();
//                }
//            } catch (Exception ignored) {}
//        });
//
//        monitorThread.start();
//
//        // Explicitly invoke the methods to be monitored
//        OverloadTest overloadTest = new OverloadTest();
//        overloadTest.add(1, 2);
//        overloadTest.add(1.0, 2.0);
//        // Run the test for a specific duration
//        Thread.sleep(2000); // Run for 6 seconds
//        stopMonitoring.set(true); // Signal the monitoring thread to stop
//
//        monitorThread.join(); // Wait for the thread to terminate
//
//        System.out.println("--- Energy Captured ---");
//        status.getMethodConsumedEnergyMap().forEach((k, v) -> System.out.printf("→ %s = %.6f J%n", k, v));
//
//        boolean found1 = false, found3 = false;
//        for (Map.Entry<String, Double> entry : status.getMethodConsumedEnergyMap().entrySet()) {
//            String method = entry.getKey();
//            double energy = entry.getValue();
//
//            if (method.equals("add(int,int)")) {
//                assertTrue(energy > 0, "add(int,int) should have non-zero energy");
//                found1 = true;
//            } else if (method.contains("add(double,double)")) {
//                assertTrue(energy > 10, "add(double,double) should have non-zero energy");
//                found3 = true;
//            }
//        }
//
//        assertTrue(found1, "add(int,int) should have non-zero energy");
//        assertTrue(found3, "add(double,double) should have non-zero energy");
//    }
//
//
//    @Test
//    public void testOneMethod() throws Exception {
//        // Start energy profiling
//        MonitoringHandler.reset();  // custom method to clear previous data
//        MonitoringHandler.start();
//
//        // Run Main1
//        OverloadTest.main(new String[0]);
//
//        // Stop energy profiling
//        MonitoringHandler.stop();
//
//        // Get the energy data
//        Map<String, Double> energyMap = MonitoringHandler.getMethodToEnergyMap();
//
//        boolean fooStringFound = false;
//        boolean otherOverloadsCalled = false;
//        System.out.println("energyMap = " + energyMap);
//        for (Map.Entry<String, Double> entry : energyMap.entrySet()) {
//            String methodSig = entry.getKey();
//            double energy = entry.getValue();
//
//            System.out.println(methodSig + " -> " + energy + " J");
//            System.out.println(methodSig);
//            if (methodSig.contains("Overload.add(int, int)")) {
//                fooStringFound = true;
//                assertTrue(energy > 0, "Energy for Overload.add(int, int) should be > 0");
//            } else if (methodSig.contains("Overload.add(int, int, int)")) {
//                otherOverloadsCalled = true;
//            }
//            else if (methodSig.contains("Overload.add(double, double)")) {
//                otherOverloadsCalled = true;
//            }
//        }
//
//        assertTrue(fooStringFound, "Overload.add(int, int) should have been invoked");
//        assertFalse(otherOverloadsCalled, "Overload.add(int) should NOT have been invoked");
//    }
//    @Test
//    public void testMonitoringOfOverloadedMethods() throws Exception {
//        // Start monitoring
//        MonitoringHandler.reset();  // Clear previous data
//        MonitoringHandler.start();
//
//        // Invoke overloaded methods
//        OverloadTest overloadTest = new OverloadTest();
//        overloadTest.add(1, 2);          // int, int
//        overloadTest.add(1.0, 2.0);      // double, double
//        overloadTest.add(1, 2, 3);       // int, int, int
//
//        // Stop monitoring
//        MonitoringHandler.stop();
//
//        // Retrieve monitored data
//        Map<String, Double> energyMap = MonitoringHandler.getMethodToEnergyMap();
//
//        // Verify each overloaded method is captured
//        boolean intIntFound = false, doubleDoubleFound = false, intIntIntFound = false;
//        for (Map.Entry<String, Double> entry : energyMap.entrySet()) {
//            String methodSig = entry.getKey();
//            double energy = entry.getValue();
//
//            if (methodSig.contains("OverloadTest.add(int, int)")) {
//                intIntFound = true;
//                System.out.println(energy);
//                assertTrue(energy > 0, "Energy for OverloadTest.add(int, int) should be > 0");
//            } else if (methodSig.contains("OverloadTest.add(double, double)")) {
//                System.out.println(energy);
//
//                doubleDoubleFound = true;
//                assertTrue(energy > 0, "Energy for OverloadTest.add(double, double) should be > 0");
//            } else if (methodSig.contains("OverloadTest.add(int, int, int)")) {
//                System.out.println(energy);
//                intIntIntFound = true;
//                assertTrue(energy > 0, "Energy for OverloadTest.add(int, int, int) should be > 0");
//            }
//        }
//
//        // Assert all overloaded methods are monitored
//        assertTrue(intIntFound, "OverloadTest.add(int, int) should have been monitored");
//        assertTrue(doubleDoubleFound, "OverloadTest.add(double, double) should have been monitored");
//        assertTrue(intIntIntFound, "OverloadTest.add(int, int, int) should have been monitored");
//    }
//}
