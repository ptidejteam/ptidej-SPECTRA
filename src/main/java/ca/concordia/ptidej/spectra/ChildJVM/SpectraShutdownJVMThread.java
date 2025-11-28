package ca.concordia.ptidej.spectra.ChildJVM;

import ca.concordia.ptidej.spectra.Profile.Constants;
import com.jprofiler.api.controller.Controller;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

import static ca.concordia.ptidej.spectra.Profile.Launcher.executeJpcontrollerCommand;
import static ca.concordia.ptidej.spectra.Profile.Launcher.exportProfilerSnapshot;

public class SpectraShutdownJVMThread extends Thread {

    @Override
    public void run() {
        System.out.println("[Spectra] Child JVM shutdown hook running...");
            System.out.println("[Spectra] Shutdown hook running - stopping profiling...");

            try {
                // Stop all recordings
                Controller.stopCPURecording();
                Controller.stopAllocRecording();
                Controller.stopMonitorRecording();
                System.out.println("[Spectra] All recordings stopped");

                // Save snapshot
                String outputDir = System.getProperty("user.home") + "/Documents/RA/SPECTRA/Output/JProfiler/";
                String snapshotPath = outputDir + "snapshot.jps";

                // Ensure directory exists
                new File(outputDir).mkdirs();

                Controller.saveSnapshot(new File(snapshotPath));
                System.out.println("[Spectra] Snapshot saved: " + snapshotPath);
                exportProfilerSnapshot();
                System.out.println("[Spectra] Snapshot exported to CSV files in: " + outputDir);

            } catch (Exception e) {
                System.err.println("[Spectra] Error in shutdown hook: " + e.getMessage());
                e.printStackTrace();
            }
    }
}
