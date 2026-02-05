package ca.concordia.ptidej.spectra.ChildJVM;

import ca.concordia.ptidej.spectra.Profile.Constants;
import com.jprofiler.api.controller.Controller;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static ca.concordia.ptidej.spectra.Profile.Launcher.*;

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
                Controller.stopThreadProfiling();
                System.out.println("[Spectra] All recordings stopped");

                // Save snapshot
                String outputDir = Constants.SPECTRA_ROOT + "/Output/JProfiler/";
                String snapshotPath = outputDir + "snapshot.jps";

                // Ensure directory exists
                new File(outputDir).mkdirs();

                File snapshotFile = new File(snapshotPath);
                snapshotFile.createNewFile();

                Controller.saveSnapshot(snapshotFile);
                System.out.println("[Spectra] Snapshot saved: " + snapshotPath);
                exportProfilerSnapshot();
                System.out.println("[Spectra] Snapshot exported to CSV files in: " + outputDir);

            } catch (Exception e) {
                System.err.println("[Spectra] Error in shutdown hook: " + e.getMessage());
                e.printStackTrace();
            }
    }

    private static void exportProfilerSnapshot() throws Exception {
        String outputDir = Constants.SPECTRA_ROOT + Constants.JPROFILER_OUTPUT_DIR;
        try {
            final List<String> command = new ArrayList<>(Constants.JPEXPORT_COMMAND);

            System.out.println("Exporting snapshot to CSVs in: " + outputDir);
            Process process = new ProcessBuilder(command).inheritIO().start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                System.err.println("JPController command '" + command + "' returned exit code: " + exitCode);
            }
        } catch (Exception e) {
            System.err.println("Error exporting snapshot to CSVs: " + e.getMessage());
            throw e;
        }
    }
}
