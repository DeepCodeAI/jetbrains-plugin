package ai.deepcode.javaclient.core;

import ai.deepcode.javaclient.DeepCodeRestApi;
import ai.deepcode.javaclient.responses.GetFiltersResponse;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public abstract class DeepCodeUtilsBase {

  private final AnalysisDataBase analysisData;
  private final DeepCodeParamsBase deepCodeParams;
  private final DeepCodeIgnoreInfoHolderBase ignoreInfoHolder;
  private final DCLoggerBase dcLogger;

  protected DeepCodeUtilsBase(
      @NotNull AnalysisDataBase analysisData,
      @NotNull DeepCodeParamsBase deepCodeParams,
      @NotNull DeepCodeIgnoreInfoHolderBase ignoreInfoHolder,
      @NotNull DCLoggerBase dcLogger) {
    this.analysisData = analysisData;
    this.deepCodeParams = deepCodeParams;
    this.ignoreInfoHolder = ignoreInfoHolder;
    this.dcLogger = dcLogger;
    initSupportedExtentionsAndConfigFiles();
  }

  protected static Set<String> supportedExtensions = Collections.emptySet();
  protected static Set<String> supportedConfigFiles = Collections.emptySet();

  public List<Object> getAllSupportedFilesInProject(@NotNull Object project) {
    final Collection<Object> allProjectFiles = allProjectFiles(project);
    if (allProjectFiles.isEmpty()) {
      dcLogger.logWarn("Empty files list for project: " + project);
    }
    // Initial scan for .dcignore files
    allProjectFiles.stream()
        .filter(ignoreInfoHolder::is_dcignoreFile)
        .forEach(ignoreInfoHolder::update_dcignoreFileContent);
    final List<Object> result =
        allProjectFiles.stream().filter(this::isSupportedFileFormat).collect(Collectors.toList());
    if (result.isEmpty()) dcLogger.logWarn("Empty supported files list for project: " + project);
    return result;
  }

  protected abstract Collection<Object> allProjectFiles(@NotNull Object project);

  private static final long MAX_FILE_SIZE = 5242880; // 5MB in bytes

  public boolean isSupportedFileFormat(@NotNull Object file) {
    // DCLogger.getInstance().info("isSupportedFileFormat started for " + psiFile.getName());
    if (ignoreInfoHolder.isIgnoredFile(file) || isGitIgnored(file)) return false;
    final boolean result =
        getFileLength(file) < MAX_FILE_SIZE
            && (supportedExtensions.contains(getFileExtention(file))
                || supportedConfigFiles.contains(ignoreInfoHolder.getFileName(file)));
    // DCLogger.getInstance().info("isSupportedFileFormat ends for " + psiFile.getName());
    return result;
  }

  protected abstract long getFileLength(@NotNull Object file);

  protected abstract String getFileExtention(@NotNull Object file);

  protected abstract boolean isGitIgnored(@NotNull Object file);

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
