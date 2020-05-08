package ai.deepcode.jbplugin.core;

import ai.deepcode.javaclient.DeepCodeRestApi;
import ai.deepcode.javaclient.responses.EmptyResponse;
import ai.deepcode.javaclient.responses.GetFiltersResponse;
import ai.deepcode.javaclient.responses.LoginResponse;
import ai.deepcode.jbplugin.DeepCodeNotifications;
import ai.deepcode.jbplugin.ui.myTodoView;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Computable;
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

  public static void asyncUpdateCurrentFilePanel(PsiFile psiFile) {
    /*
        ApplicationManager.getApplication()
            .invokeLater(
                () ->
                    WriteCommandAction.runWriteCommandAction(
                        psiFile.getProject(),
                        () -> DeepCodeConsoleToolWindowFactory.updateCurrentFilePanel(psiFile)));
    */
  }

  public static <T> T computeInReadActionInSmartMode(
      //      @NotNull Project project, @NotNull Callable<T> computation) {
      @NotNull Project project, @NotNull final Computable<T> computation) {
    // fixme debug only
    //DCLogger.info("computeInReadActionInSmartMode requested");
    T result = null;
    final DumbService dumbService =
        ReadAction.compute(() -> project.isDisposed() ? null : DumbService.getInstance(project));
    if (dumbService == null) return result;
    result =
        dumbService.runReadActionInSmartMode(
            () -> {
              // fixme debug only
              //DCLogger.info("computeInReadActionInSmartMode actually executing");
              return computation.compute();
            });
    return result;
  }

  public static void delay(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      e.printStackTrace();
      Thread.currentThread().interrupt();
    }
  }

  public static long timeOfLastRescanRequest = 0;

  // BackgroundTaskQueue ??? com.intellij.openapi.wm.ex.StatusBarEx#getBackgroundProcesses ???
  public static void rescanProject(@Nullable Project project, long delayMilliseconds) {
    runInBackground(
        project,
        () -> {
          long timeOfThisRequest = timeOfLastRescanRequest = System.currentTimeMillis();
          delay(delayMilliseconds);
          if (timeOfLastRescanRequest > timeOfThisRequest) return;
          AnalysisData.clearCache(project);
          asyncAnalyseProjectAndUpdatePanel(project);
        });
  }

  public static void runInBackground(@Nullable Project project, @NotNull Runnable runnable) {
    DCLogger.info("runInBackground requested");
    ProgressManager.getInstance()
        .run(
            new Task.Backgroundable(project, "DeepCode: Analysing Files...") {
              @Override
              public void run(@NotNull ProgressIndicator indicator) {
                runnable.run();
              }
            });
  }

  public static void asyncAnalyseProjectAndUpdatePanel(@Nullable Project project) {
    if (project == null) {
      for (Project prj : ProjectManager.getInstance().getOpenProjects()) {
        asyncAnalyseAndUpdatePanel(prj, null);
      }
    } else asyncAnalyseAndUpdatePanel(project, null);
  }

  public static void asyncAnalyseAndUpdatePanel(
      @NotNull Project project, @Nullable Collection<PsiFile> psiFiles) {
    asyncAnalyseAndUpdatePanel(project, psiFiles, Collections.emptyList());
  }

  public static void asyncAnalyseAndUpdatePanel(
      @NotNull Project project,
      @Nullable Collection<PsiFile> psiFiles,
      @NotNull Collection<PsiFile> filesToRemove) {
    //    DumbService.getInstance(project)
    //        .runWhenSmart(
    //            () ->
    runInBackground(
        project,
        () -> {
          AnalysisData.getAnalysis(
              (psiFiles != null) ? psiFiles : getAllSupportedFilesInProject(project),
              filesToRemove);
          ServiceManager.getService(project, myTodoView.class).refresh();
          //      StatusBarUtil.setStatusBarInfo(project, message);
        });
  }

  private static List<PsiFile> getAllSupportedFilesInProject(@NotNull Project project) {
    // todo do we need indexes ready here?

    //    final DumbService dumbService = ReadAction.compute(() -> project.isDisposed() ? null :
    // DumbService.getInstance(project));
    //    if (dumbService == null) return;
    //    dumbService.runReadActionInSmartMode(() ->
    return computeInReadActionInSmartMode(
        project,
        () -> {
          final List<PsiFile> allProjectFiles = allProjectFiles(project);
          // Initial scan for .dcignore files
          allProjectFiles.stream()
              .filter(DeepCodeIgnoreInfoHolder::is_dcignoreFile)
              .forEach(DeepCodeIgnoreInfoHolder::update_dcignoreFileContent);
          return allProjectFiles.stream()
              .filter(DeepCodeUtils::isSupportedFileFormat)
              .collect(Collectors.toList());
        });
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
    DCLogger.info(
        ((isLogged) ? "Logging check succeed." : "Logging check fails: " + message)
            + " Token: "
            + sessionToken);
    if (!isLogged && userActionNeeded) {
      if (sessionToken.isEmpty() && response.getStatusCode() == 401) {
        message = "Authenticate using your GitHub, Bitbucket or GitLab account";
      }
      DeepCodeNotifications.showLoginLink(project, message);
    } else if (isLogged && project != null && !DeepCodeParams.consentGiven(project)) {
      DCLogger.info("Consent check fail! Project: " + project.getName());
      isLogged = false;
      DeepCodeNotifications.showConsentRequest(project, userActionNeeded);
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
    DeepCodeNotifications.showInfo("Logging succeed", project);
    AnalysisData.clearCache(project);
    DeepCodeUtils.asyncAnalyseProjectAndUpdatePanel(project);
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
      DCLogger.info(
          "Can't retrieve supported file extensions and config files from the server. Fallback to default set.\n"
              + filtersResponse.getStatusCode()
              + " "
              + filtersResponse.getStatusDescription());
      supportedExtensions =
          new HashSet<>(
              Arrays.asList(
                  "cc", "htm", "cpp", "c", "vue", "h", "hpp", "es6", "js", "py", "es", "jsx",
                  "java", "tsx", "html", "ts"));
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
