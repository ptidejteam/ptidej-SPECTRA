/*
 * Copyright (c) 2021-2024, Adel Noureddine, Université de Pau et des Pays de l'Adour.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the
 * GNU General Public License v3.0 only (GPL-3.0-only)
 * which accompanies this distribution, and is available at
 * https://www.gnu.org/licenses/gpl-3.0.en.html
 *
 */

package org.noureddine.joularjx.utils;

import com.sun.management.OperatingSystemMXBean;
import org.junit.jupiter.api.Test;
import org.noureddine.joularjx.monitor.MonitoringHandler;
import org.noureddine.joularjx.monitor.MonitoringStatus;
import org.noureddine.joularjx.result.ResultWriter;
import org.noureddine.joularjx.cpu.Cpu;
import org.noureddine.joularjx.result.ResultTreeManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Map;

public class CallTreeTest {

    @Test
    public void getCallTreeTest() {
        StackTraceElement[] stackTraceArray = Thread.currentThread().getStackTrace();

        CallTree stackTrace = new CallTree(stackTraceArray);

        assertEquals(stackTrace.getCallTree(), Arrays.asList(stackTraceArray));
    }

    @Test
    public void setCallTreeTest() {
        CallTree stackTrace = new CallTree();
        StackTraceElement[] stackTraceArray = Thread.currentThread().getStackTrace();

        stackTrace.setCallTree(stackTraceArray);

        assertEquals(stackTrace.getCallTree(), Arrays.asList(stackTraceArray));
    }

    @Test
    public void equalsTest() {
        StackTraceElement[] stackTraceArray = Thread.currentThread().getStackTrace();

        CallTree stackTrace = new CallTree(stackTraceArray);

        assertTrue(stackTrace.equals(new CallTree(stackTraceArray)));
        assertFalse(stackTrace.equals(new CallTree()));
    }

    @Test
    public void equalsWithTheSameElementsButNotTheSameOrderTest() {
        StackTraceElement e = new StackTraceElement("ClassA", "MethodA", "FileA", 20);
        StackTraceElement e1 = new StackTraceElement("ClassB", "MethodB", "FileB", 12);
        StackTraceElement e2 = new StackTraceElement("ClassC", "MethodC", "FileC", 59);

        StackTraceElement[] arr1 = {e, e1, e2};
        StackTraceElement[] arr2 = {e2, e1, e};

        CallTree s1 = new CallTree(arr1);
        CallTree s2 = new CallTree(arr2);

        assertFalse(s1.equals(s2));
    }

    @Test
    public void equalsWithSameElementsButNotSameLinesTest() {
        StackTraceElement e = new StackTraceElement("ClassA", "MethodA", "FileA", 20);
        StackTraceElement e1 = new StackTraceElement("ClassB", "MethodB", "FileB", 12);

        StackTraceElement e2 = new StackTraceElement("ClassA", "MethodA", "FileA", 59);
        StackTraceElement e3 = new StackTraceElement("ClassB", "MethodB", "FileB", 02);

        StackTraceElement[] arr1 = {e, e1};
        StackTraceElement[] arr2 = {e2, e3};

        CallTree s1 = new CallTree(arr1);
        CallTree s2 = new CallTree(arr2);

        assertEquals(s1, s2);
    }

    @Test
    public void equalsWithoutFileNameDoesNotFailsTest() {
        StackTraceElement e = new StackTraceElement("ClassA", "MethodA", null, 20);
        StackTraceElement e1 = new StackTraceElement("ClassB", "MethodB", null, 12);
        StackTraceElement e2 = new StackTraceElement("ClassC", "MethodC", null, 59);

        StackTraceElement[] arr1 = {e, e1, e2};
        StackTraceElement[] arr2 = {e, e1, e2};

        CallTree s1 = new CallTree(arr1);
        CallTree s2 = new CallTree(arr2);

        assertTrue(s1.equals(s2));
    }

    @Test
    public void toSringTest() {
        StackTraceElement e = new StackTraceElement("ClassA", "MethodA", "FileA", 20);
        StackTraceElement e1 = new StackTraceElement("ClassB", "MethodB", "FileB", 12);
        StackTraceElement e2 = new StackTraceElement("ClassC", "MethodC", "FileC", 59);

        StackTraceElement[] arr = {e2, e1, e};
        CallTree stackTrace = new CallTree(arr);

        String oracle = "ClassA.MethodA();ClassB.MethodB();ClassC.MethodC()";

        assertEquals(oracle, stackTrace.toString());
    }

    @Test
    public void toStringWithParametersTest() {
        StackTraceElement e = new StackTraceElement("org.noureddine.joularjx.monitor.MonitoringHandler", "destroyingVM",
                "MonitoringHandler.java", 402);
        StackTraceElement[] arr = {e};
        CallTree stackTrace = new CallTree(arr);

        String oracle = "org.noureddine.joularjx.monitor.MonitoringHandler.destroyingVM()";
        System.out.println(stackTrace.toString());
        assertEquals(oracle, stackTrace.toString());
    }

    @Test
    public void testCorrectMethod() {
        String resolved = CallTree.resolve("org.noureddine.joularjx.monitor.MonitoringHandler"
                , "destroyingVM", 402);
        assertEquals("org.noureddine.joularjx.monitor.MonitoringHandler.destroyingVM()", resolved);
    }
    @Test
    public void testCorrectMethod2() {
        String resolved = CallTree.resolve("org.noureddine.joularjx.monitor.MonitoringHandler"
                , "destroyingVM", 410);
        assertEquals("org.noureddine.joularjx.monitor.MonitoringHandler.destroyingVM(double)", resolved);
    }
    @Test
    public void testCorrectMethod3() {
        String resolved = CallTree.resolve("org.noureddine.joularjx.monitor.MonitoringHandler"
                , "destroyingVM", 418);
        System.out.println(resolved);
        assertEquals("org.noureddine.joularjx.monitor.MonitoringHandler.destroyingVM(String)", resolved);
    }
    @Test
    public void testCorrectConstructor() {
        String resolved = CallTree.resolve("org.noureddine.joularjx.utils.CallTree", "<init>", 38);
        assertEquals("org.noureddine.joularjx.monitor.MonitoringHandler.CallTree.<init>()", resolved);
    }
    @Test
    public void testCorrectConstructor2() {
        String resolved = CallTree.resolve("org.noureddine.joularjx.utils.CallTree", "<init>.List<StackTraceElement>", 56);
        assertEquals("org.noureddine.joularjx.monitor.MonitoringHandler.CallTree.<init>()", resolved);
    }
    static class OverloadTester {
        public void test(String s) {
            System.out.println("Overload 1: " + s);
        }

        public void test(String s, int x) {
            System.out.println("Overload 2: " + s + ", " + x);
        }

        public void test(String s, int x, double y) {
            System.out.println("Overload 3: " + s + ", " + x + ", " + y);
        }
    }

    @Test
    void testEnergyOfAllOverloadedMethods() throws Exception {
        OverloadTester tester = new OverloadTester();

        // Simulate CPU load for profiling
        for (int i = 0; i < 1000; i++) {
            tester.test("a");
            tester.test("a", 1);
            tester.test("a", 1, 2.0);
        }

        // Setup stubbed components
        AgentProperties props = new AgentProperties();
        MonitoringStatus status = new MonitoringStatus();
        ResultWriter writer = new ResultWriter() {
            @Override public void setTarget(String path, boolean overwrite) {}
            @Override public void write(String key, double value) {}
            @Override public void closeTarget() {}
        };
        Cpu cpu = new Cpu() {
            @Override
            public void close() throws Exception {

            }

            @Override
            public void initialize() {

            }

            @Override public double getInitialPower() { return 0.0; }
            @Override public double getCurrentPower(double cpuLoad) { return 1.0; }
            @Override public double getMaxPower(double cpuLoad) { return 0.0; }
        };

        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        ResultTreeManager resultTreeManager = new ResultTreeManager(props, 1234L, System.currentTimeMillis());

        MonitoringHandler handler = new MonitoringHandler(1234L, props, writer, cpu, status, osBean, threadMXBean, resultTreeManager);

        // Only run once, so we wrap in thread and cancel early
        AtomicBoolean shouldStop = new AtomicBoolean(false);
        Thread t = new Thread(() -> {
            try {
                handler.run();
            } catch (Exception ignored) {}
        });
        t.start();

        // Wait enough for sampling to occur
        Thread.sleep(2500);
        t.interrupt();
        t.join();

        // Now check if each overload had energy > 0 (keyed by resolved name)
        boolean found1 = false, found2 = false, found3 = false;
        for (Map.Entry<String, Double> entry : status.getMethodConsumedEnergyMap().entrySet()) {
            String method = entry.getKey();
            double energy = entry.getValue();
            if (method.contains("destroyingVM()")) found1 = energy > 0;
            if (method.contains("destroyingVM(String)")) found2 = energy > 0;
            if (method.contains("destroyingVM(double)")) found3 = energy > 0;
        }

        assertTrue(found1, "Expected destroyingVM() to consume energy");
        assertTrue(found2, "Expected destroyingVM(String) to consume energy");
        assertTrue(found3, "Expected destroyingVM(double) to consume energy");
    }
    @Test
    void testResolvedOverloadsInCallTree() throws Exception {
        CallTreeTest.OverloadTester tester = new CallTreeTest.OverloadTester();

        // Call each overload once to push it into the stack trace
        tester.test("A");
        tester.test("B", 10);
        tester.test("C", 20, 3.14);

        // Generate the call stack manually (simulate sampling one frame deep)
        List<StackTraceElement> stack = List.of(Thread.currentThread().getStackTrace());

        // Create a CallTree for this frame
        CallTree tree = new CallTree(stack);

        // Print the resolved method name (calls resolve() internally)
        String resolved = tree.toString();
        System.out.println("Resolved CallTree: " + resolved);

        // We expect the top frame to contain one of the tester.test(...) calls
        // Since only one will appear on top, this assert is illustrative:
        assertTrue(resolved.contains("test(String)")
                        || resolved.contains("test(String,int)")
                        || resolved.contains("test(String,int,double)"),
                "Resolved method should be one of the overloaded variants");
    }


}




