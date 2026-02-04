package ca.concordia.ptidej.spectra.Profile;

import java.util.List;

public final class Constants {
        public static final String PROJECT_ROOT = "/home/laurent/sandbox/java/ptidej-Ptidej/CPL"; // Project to profile
        public static final String SPECTRA_ROOT = "/home/laurent/sandbox/java/ptidej-SPECTRA";
        public static final String JPROFILER_BIN_PATH = "/opt/jprofiler14/bin";
        public static final String JOULARJX_PATH = SPECTRA_ROOT + "/src/main/resources/joularjx-3.0.1.jar";
        public static final String JPROFILER_AGENT = JPROFILER_BIN_PATH + "/linux-x64/libjprofilerti.so";
        public static final String MY_AGENT_PATH = SPECTRA_ROOT + "/target/Spectra-with-dependencies.jar";
        public static final String MY_JPROFILER_AGENT_PATH = SPECTRA_ROOT + "/target/spectra-jprofiler-agent.jar";

        public static final String JPCONTROLLER_PATH = JPROFILER_BIN_PATH + "/jpcontroller";
        public static final List<String> JPEXPORT_COMMAND = List.of(
                JPROFILER_BIN_PATH + "/jpexport",
                SPECTRA_ROOT + "/Output/JProfiler/snapshot.jps", "AllObjects", "-format=csv",
                SPECTRA_ROOT + "/Output/JProfiler/allobjects.csv", "CallTree", "-format=xml",
                "-aggregation=method", SPECTRA_ROOT + "/Output/JProfiler/calltree.xml",
                "Hotspots", "-format=csv", SPECTRA_ROOT + "/Output/JProfiler/hotspots.csv");

    }