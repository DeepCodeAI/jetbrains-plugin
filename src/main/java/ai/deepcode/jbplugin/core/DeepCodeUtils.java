package ai.deepcode.jbplugin.core;

import ai.deepcode.javaclient.DeepCodeRestApi;
import ai.deepcode.javaclient.responses.EmptyResponse;
import ai.deepcode.javaclient.responses.GetFiltersResponse;
import ai.deepcode.javaclient.responses.LoginResponse;
import ai.deepcode.jbplugin.DeepCodeNotifications;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.concurrency.NonUrgentExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public final class DeepCodeUtils {
  private DeepCodeUtils() {}

  private static Set<String> supportedExtensions = Collections.emptySet();
  private static Set<String> supportedConfigFiles = Collections.emptySet();
  private static final String userAgent =
      "JetBrains-plugin-"
          + ApplicationNamesInfo.getInstance().getProductName()
          + "-"
          + ApplicationInfo.getInstance().getFullVersion();

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
    final PsiDirectory prjDirectory = psiManager.findDirectory(project.getBaseDir());
    return prjDirectory != null ? getFilesRecursively(prjDirectory) : Collections.emptyList();
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

  /** network request! */
  public static boolean isLogged(@Nullable Project project, boolean userActionNeeded) {
    final String sessionToken = DeepCodeParams.getSessionToken();
    final EmptyResponse response = DeepCodeRestApi.checkSession(sessionToken);
    boolean isLogged = response.getStatusCode() == 200;
    String message = response.getStatusDescription();
    if (isLogged) {
      DCLogger.info("Login check succeed." + " Token: " + sessionToken);
    } else {
      DCLogger.warn("Login check fails: " + message + " Token: " + sessionToken);
    }
    if (!isLogged && userActionNeeded) {
      if (sessionToken.isEmpty() && response.getStatusCode() == 401) {
        message = "Authenticate using your GitHub, Bitbucket or GitLab account";
      }
      DeepCodeNotifications.showLoginLink(project, message);
    } else if (isLogged && project != null) {
      if (DeepCodeParams.consentGiven(project)) {
        DCLogger.info("Consent check succeed for: " + project);
      } else {
        DCLogger.warn("Consent check fail! Project: " + project.getName());
        isLogged = false;
        DeepCodeNotifications.showConsentRequest(project, userActionNeeded);
      }
    }
    return isLogged;
  }

  /** network request! */
  public static void requestNewLogin(@NotNull Project project) {
    DeepCodeParams.clearLoginParams();
    LoginResponse response = DeepCodeRestApi.newLogin(userAgent);
    if (response.getStatusCode() == 200) {
      DeepCodeParams.setSessionToken(response.getSessionToken());
      DeepCodeParams.setLoginUrl(response.getLoginURL());
      BrowserUtil.open(DeepCodeParams.getLoginUrl());
      if (!isLoginCheckLoopStarted) {
        ReadAction.nonBlocking(() -> startLoginCheckLoop(project))
            .submit(NonUrgentExecutor.getInstance());
      }
    } else {
      DeepCodeNotifications.showError(response.getStatusDescription(), project);
    }
  }

  private static boolean isLoginCheckLoopStarted = false;

  private static void startLoginCheckLoop(@NotNull Project project) {
    isLoginCheckLoopStarted = true;
    do {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        e.printStackTrace();
        Thread.currentThread().interrupt();
      }
      ProgressManager.checkCanceled();
    } while (!isLogged(project, false));
    isLoginCheckLoopStarted = false;
    DeepCodeNotifications.showInfo("Login succeed", project);
    AnalysisData.clearCache(project);
    RunUtils.asyncAnalyseProjectAndUpdatePanel(project);
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
