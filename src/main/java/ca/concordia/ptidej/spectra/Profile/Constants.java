package ca.concordia.ptidej.spectra.Profile;

import java.util.List;

public final class Constants {
        public static final String PROJECT_ROOT = "/Users/mac/Documents/RA/SPECTRA";
        public static final String JOULARJX_PATH = PROJECT_ROOT + "/src/main/resources/joularjx-3.0.1.jar";
        public static final String JPROFILER_AGENT = "/Applications/JProfiler.app/Contents/Resources/app/bin/macos/libjprofilerti.jnilib";
        public static final String MY_AGENT_PATH = PROJECT_ROOT + "/target/Spectra-with-dependencies.jar";
        public static final String JPCONTROLLER_PATH = "/Applications/JProfiler.app/Contents/Resources/app/bin/jpcontroller";
        public static final List<String> JPEXPORT_COMMAND = List.of(
                "/Applications/JProfiler.app/Contents/Resources/app/bin/jpexport",
                PROJECT_ROOT + "/output/jprofiler/snapshot.jps", "AllObjects", "-format=csv",
                PROJECT_ROOT + "/output/Jprofiler/allobjects.csv", "CallTree", "-format=xml",
                "-aggregation=method", PROJECT_ROOT + "/output/Jprofiler/calltree.csv.xml",
                "Hotspots", "-format=csv", PROJECT_ROOT + "/output/Jprofiler/hotspots.csv");

    }