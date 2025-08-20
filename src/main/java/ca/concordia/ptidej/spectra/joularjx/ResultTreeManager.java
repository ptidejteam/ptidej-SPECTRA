package ca.concordia.ptidej.spectra.joularjx;
import org.noureddine.joularjx.utils.AgentProperties;


import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.noureddine.joularjx.utils.JoularJXLogging;

public class ResultTreeManager  extends  org.noureddine.joularjx.result.ResultTreeManager {
    private static final Logger logger = JoularJXLogging.getLogger();
    public static final String GLOBAL_RESULT_DIRECTORY_NAME = "joularjx-result";
    public static final String ALL_DIRECTORY_NAME = "all";
    public static final String FILTERED_DIRECTORY_NAME = "app";
    public static final String RUNTIME_DIRECTORY_NAME = "runtime";
    public static final String TOTAL_DIRECTORY_NAME = "total";
    public static final String EVOLUTION_DIRECTORY_NAME = "evolution";
    public static final String CALLTREE_DIRECTORY_NAME = "calltrees";
    public static final String METHOD_DIRECTORY_NAME = "methods";
    private AgentProperties properties;
    private String runDirectoryPath;
    private String allTotalMethodsPath;
    private String filteredTotalMethodsPath;
    private String allRuntimeMethodsPath;
    private String filteredRuntimeMethodsPath;
    private String allRuntimeCallTreePath;
    private String filteredRuntimeCallTreePath;
    private String allTotalCallTreePath;
    private String filteredTotalCallTreePath;
    private String allEvolutionPath;
    private String filteredEvolutionPath;

    public ResultTreeManager(AgentProperties properties, long pid, long startTimestamp) {
        super(properties, pid, startTimestamp);
        this.properties = properties;
        Object[] var10002 = new Object[]{pid, startTimestamp};
        this.runDirectoryPath = "Output/Joularjx/data/";
        String allDirectoryPath = this.runDirectoryPath + "/all";
        String filteredDirectoryPath = this.runDirectoryPath + "/app";
        this.allTotalMethodsPath = allDirectoryPath + "/total/methods";
        this.filteredTotalMethodsPath = filteredDirectoryPath + "/total/methods";
        this.allRuntimeMethodsPath = allDirectoryPath + "/runtime/methods";
        this.filteredRuntimeMethodsPath = filteredDirectoryPath + "/runtime/methods";
        this.allRuntimeCallTreePath = allDirectoryPath + "/runtime/calltrees";
        this.filteredRuntimeCallTreePath = filteredDirectoryPath + "/runtime/calltrees";
        this.allTotalCallTreePath = allDirectoryPath + "/total/calltrees";
        this.filteredTotalCallTreePath = filteredDirectoryPath + "/total/calltrees";
        this.allEvolutionPath = allDirectoryPath + "/evolution";
        this.filteredEvolutionPath = filteredDirectoryPath + "/evolution";
    }

    public boolean create() {
        boolean verif = true;
        logger.log(Level.INFO, String.format("Results will be stored in %s", this.runDirectoryPath));
        List<String> directoriesToCreate = new ArrayList();
        directoriesToCreate.add(this.allTotalMethodsPath);
        directoriesToCreate.add(this.filteredTotalMethodsPath);
        if (this.properties.savesRuntimeData()) {
            directoriesToCreate.add(this.allRuntimeMethodsPath);
            directoriesToCreate.add(this.filteredRuntimeMethodsPath);
        }

        if (this.properties.callTreesConsumption()) {
            if (this.properties.saveCallTreesRuntimeData()) {
                directoriesToCreate.add(this.allRuntimeCallTreePath);
                directoriesToCreate.add(this.filteredRuntimeCallTreePath);
            }

            directoriesToCreate.add(this.allTotalCallTreePath);
            directoriesToCreate.add(this.filteredTotalCallTreePath);
        }

        if (this.properties.trackConsumptionEvolution()) {
            directoriesToCreate.add(this.allEvolutionPath);
            directoriesToCreate.add(this.filteredEvolutionPath);
        }

        for(String dirPath : directoriesToCreate) {
            File dir = new File(dirPath);
            if (!dir.exists() && !dir.mkdirs()) {
                logger.log(Level.WARNING, String.format("Failed to create directory %s", dirPath));
                verif = false;
            }
        }

        return verif;
    }

    public String getAllRuntimeMethodsPath() {
        return this.allRuntimeMethodsPath;
    }

    public String getAllTotalMethodsPath() {
        return this.allTotalMethodsPath;
    }

    public String getFilteredRuntimeMethodsPath() {
        return this.filteredRuntimeMethodsPath;
    }

    public String getFilteredTotalMethodsPath() {
        return this.filteredTotalMethodsPath;
    }

    public String getAllRuntimeCallTreePath() {
        return this.allRuntimeCallTreePath;
    }

    public String getAllTotalCallTreePath() {
        return this.allTotalCallTreePath;
    }

    public String getFilteredRuntimeCallTreePath() {
        return this.filteredRuntimeCallTreePath;
    }

    public String getFilteredTotalCallTreePath() {
        return this.filteredTotalCallTreePath;
    }

    public String getAllEvolutionPath() {
        return this.allEvolutionPath;
    }

    public String getFilteredEvolutionPath() {
        return this.filteredEvolutionPath;
    }
}

