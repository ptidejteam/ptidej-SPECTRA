package ca.concordia.ptidej.spectra.Profile;

import java.util.List;

public final class Constants {
        public static final String PROJECT_ROOT = "/Users/mac/Documents/RA/SPECTRA";
        public static final String JPROFILER_AGENT = "/Applications/JProfiler.app/Contents/Resources/app/bin/macos/libjprofilerti.jnilib";
        public static final String MY_AGENT_PATH = PROJECT_ROOT + "/target/Spectra-with-dependencies.jar";
        public static final String JPCONTROLLER_PATH = "/Applications/JProfiler.app/Contents/Resources/app/bin/jpcontroller";
        public static final List<String> JPEXPORT_COMMAND = List.of(
                "/Applications/JProfiler.app/Contents/Resources/app/bin/jpexport",
                PROJECT_ROOT + "/Output/JProfiler/snapshot.jps", "CallTree", "-format=xml",
                "-aggregation=method", PROJECT_ROOT + "/Output/JProfiler/calltree.csv.xml",
                "Hotspots", "-format=csv", PROJECT_ROOT + "/Output/JProfiler/hotspots.csv");
        
        public static final String JPROFILER_AVG_CSV = PROJECT_ROOT + "/Output/JProfiler/calltree-averaged.csv";
        public static final String JOULARJX_AVG_CSV = PROJECT_ROOT + "/Output/Joularjx/joularJX-averaged-all-methods-energy.csv";
        public static final String JOULARJX_DIR = PROJECT_ROOT + "/Output/Joularjx";
}