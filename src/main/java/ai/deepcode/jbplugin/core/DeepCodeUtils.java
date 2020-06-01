package ai.deepcode.jbplugin.core;

import ai.deepcode.javaclient.DeepCodeRestApi;
import ai.deepcode.javaclient.responses.GetFiltersResponse;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public final class DeepCodeUtils {
  private DeepCodeUtils() {}

  private static Set<String> supportedExtensions = Collections.emptySet();
  private static Set<String> supportedConfigFiles = Collections.emptySet();

  static List<PsiFile> getAllSupportedFilesInProject(@NotNull Project project) {
    final List<PsiFile> result =
        RunUtils.computeInReadActionInSmartMode(
            project,
            () -> {
              final List<PsiFile> allProjectFiles = allProjectFiles(project);
              if (allProjectFiles.isEmpty()) {
                DCLogger.warn("Empty files list for project: " + project);
              }
              // Initial scan for .dcignore files
              allProjectFiles.stream()
                  .filter(DeepCodeIgnoreInfoHolder::is_dcignoreFile)
                  .forEach(DeepCodeIgnoreInfoHolder::update_dcignoreFileContent);
              return allProjectFiles.stream()
                  .filter(DeepCodeUtils::isSupportedFileFormat)
                  .collect(Collectors.toList());
            });
    if (result.isEmpty()) DCLogger.warn("Empty supported files list for project: " + project);
    return result;
  }

  private static List<PsiFile> allProjectFiles(@NotNull Project project) {
    final PsiManager psiManager = PsiManager.getInstance(project);
    final VirtualFile projectDir = ProjectUtil.guessProjectDir(project);
    if (projectDir == null) {
      DCLogger.warn("Project directory not found for: " + project);
      return Collections.emptyList();
    }
    final PsiDirectory prjDirectory = psiManager.findDirectory(projectDir);
    if (prjDirectory == null) {
      DCLogger.warn("Project directory not found for: " + project);
      return Collections.emptyList();
    }
    return getFilesRecursively(prjDirectory);
  }

  private static List<PsiFile> getFilesRecursively(@NotNull PsiDirectory psiDirectory) {
    List<PsiFile> psiFileList = new ArrayList<>(Arrays.asList(psiDirectory.getFiles()));
    for (PsiDirectory subDir : psiDirectory.getSubdirectories()) {
      psiFileList.addAll(getFilesRecursively(subDir));
    }
    return psiFileList;
  }

  // todo mapFile2EWI at AnalysisData
  public static ErrorsWarningsInfos getEWI(Collection<PsiFile> psiFiles) {
    int errors = 0;
    int warnings = 0;
    int infos = 0;
    Set<String> countedSuggestions = new HashSet<>();
    for (PsiFile file : psiFiles) {
      for (SuggestionForFile suggestion : AnalysisData.getAnalysis(file)) {
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

  private static final long MAX_FILE_SIZE = 5242880; // 5MB in bytes

  public static boolean isSupportedFileFormat(PsiFile psiFile) {
    // fixme debug only
    // DCLogger.info("isSupportedFileFormat started for " + psiFile.getName());
    if (supportedExtensions.isEmpty() || supportedConfigFiles.isEmpty()) {
      initSupportedExtentionsAndConfigFiles();
    }
    if (psiFile == null) return false;
    if (DeepCodeIgnoreInfoHolder.isIgnoredFile(psiFile)) return false;
    final VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) return false;
    if (ChangeListManager.getInstance(psiFile.getProject()).isIgnoredFile(virtualFile))
      return false;
    final boolean result =
        virtualFile.getLength() < MAX_FILE_SIZE
            && (supportedExtensions.contains(virtualFile.getExtension())
                || supportedConfigFiles.contains(virtualFile.getName()));
    // fixme debug only
    // DCLogger.info("isSupportedFileFormat ends for " + psiFile.getName());
    return result;
  }

  /** Potentially <b>Heavy</b> network request! */
  private static void initSupportedExtentionsAndConfigFiles() {
    GetFiltersResponse filtersResponse =
        DeepCodeRestApi.getFilters(DeepCodeParams.getSessionToken());
    if (filtersResponse.getStatusCode() == 200) {
      supportedExtensions =
          filtersResponse.getExtensions().stream()
              .map(s -> s.substring(1)) // remove preceding `.` (`.js` -> `js`)
              .collect(Collectors.toSet());
      supportedConfigFiles = new HashSet<>(filtersResponse.getConfigFiles());
      DCLogger.info("Supported extensions: " + supportedExtensions);
      DCLogger.info("Supported configFiles: " + supportedConfigFiles);
    } else {
      DCLogger.warn(
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

  public static String getDeepCodedFilePath(PsiFile psiFile) {
    // looks like we don't need ReadAction for this (?)
    String absolutePath = psiFile.getVirtualFile().getPath();
    final String projectPath = psiFile.getProject().getBasePath();
    if (projectPath != null) {
      absolutePath = absolutePath.replace(projectPath, "");
    }
    return absolutePath.startsWith("/") ? absolutePath : "/" + absolutePath;
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
