package ai.deepcode.javaclient.core;

import ai.deepcode.javaclient.DeepCodeRestApi;
import ai.deepcode.javaclient.responses.GetFiltersResponse;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public abstract class DeepCodeUtilsBase {

  private final AnalysisDataBase analysisData;
  private final DeepCodeParamsBase deepCodeParams;
  private final DCLoggerBase dcLogger;

  protected DeepCodeUtilsBase(
          @NotNull AnalysisDataBase analysisData,
          @NotNull DeepCodeParamsBase deepCodeParams,
          @NotNull DCLoggerBase dcLogger) {
    this.analysisData = analysisData;
    this.deepCodeParams = deepCodeParams;
    this.dcLogger = dcLogger;
    initSupportedExtentionsAndConfigFiles();
  }

  protected static Set<String> supportedExtensions = Collections.emptySet();
  protected static Set<String> supportedConfigFiles = Collections.emptySet();

  abstract public List<Object> getAllSupportedFilesInProject(@NotNull Object project);

  protected static final long MAX_FILE_SIZE = 5242880; // 5MB in bytes

  abstract public boolean isSupportedFileFormat(Object file);

  /** Potentially <b>Heavy</b> network request! */
  private void initSupportedExtentionsAndConfigFiles() {
    GetFiltersResponse filtersResponse =
        DeepCodeRestApi.getFilters(deepCodeParams.getSessionToken());
    if (filtersResponse.getStatusCode() == 200) {
      supportedExtensions =
          filtersResponse.getExtensions().stream()
              .map(s -> s.substring(1)) // remove preceding `.` (`.js` -> `js`)
              .collect(Collectors.toSet());
      supportedConfigFiles = new HashSet<>(filtersResponse.getConfigFiles());
      dcLogger.logInfo("Supported extensions: " + supportedExtensions);
      dcLogger.logInfo("Supported configFiles: " + supportedConfigFiles);
    } else {
      dcLogger.logWarn(
          "Can't retrieve supported file extensions and config files from the server. Fallback to default set.\n"
              + filtersResponse.getStatusCode()
              + " "
              + filtersResponse.getStatusDescription());
      supportedExtensions =
          new HashSet<>(
              Arrays.asList(
                  "cc", "htm", "cpp", "cxx", "c", "vue", "h", "hpp", "hxx", "es6", "js", "py", "es",
                  "jsx", "java", "tsx", "html", "ts"));
      supportedConfigFiles =
          new HashSet<>(
              Arrays.asList(
                  "pylintrc",
                  "ruleset.xml",
                  ".eslintrc.json",
                  ".pylintrc",
                  ".eslintrc.js",
                  "tslint.json",
                  ".pmdrc.xml",
                  ".ruleset.xml",
                  ".eslintrc.yml"));
    }
  }

  // todo mapFile2EWI at AnalysisData
  public ErrorsWarningsInfos getEWI(Collection<Object> files) {
    int errors = 0;
    int warnings = 0;
    int infos = 0;
    Set<String> countedSuggestions = new HashSet<>();
    for (Object file : files) {
      for (SuggestionForFile suggestion : analysisData.getAnalysis(file)) {
        if (!countedSuggestions.contains(suggestion.getId())) {
          final int severity = suggestion.getSeverity();
          if (severity == 1) infos += 1;
          else if (severity == 2) warnings += 1;
          else if (severity == 3) errors += 1;
          countedSuggestions.add(suggestion.getId());
        }
      }
    }
    return new ErrorsWarningsInfos(errors, warnings, infos);
  }

  public static class ErrorsWarningsInfos {
    private final int errors;
    private final int warnings;
    private final int infos;

    public ErrorsWarningsInfos(int errors, int warnings, int infos) {
      this.errors = errors;
      this.warnings = warnings;
      this.infos = infos;
    }

    public int getErrors() {
      return errors;
    }

    public int getWarnings() {
      return warnings;
    }

    public int getInfos() {
      return infos;
    }
  }
}
