package ai.deepcode.jbplugin.utils;

import ai.deepcode.javaclient.DeepCodeRestApi;
import ai.deepcode.javaclient.responses.GetFiltersResponse;
import ai.deepcode.javaclient.responses.LoginResponse;
import ai.deepcode.jbplugin.DeepCodeNotifications;
import ai.deepcode.jbplugin.DeepCodeToolWindowFactory;
import ai.deepcode.jbplugin.ui.myTodoView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static ai.deepcode.jbplugin.utils.DeepCodeParams.setLoginUrl;
import static ai.deepcode.jbplugin.utils.DeepCodeParams.setSessionToken;

public final class DeepCodeUtils {
  private static Set<String> supportedExtensions = Collections.emptySet();

  private DeepCodeUtils() {}

  public static void asyncUpdateCurrentFilePanel(PsiFile psiFile) {
    ApplicationManager.getApplication()
        .invokeLater(
            () ->
                WriteCommandAction.runWriteCommandAction(
                    psiFile.getProject(),
                    () -> DeepCodeToolWindowFactory.updateCurrentFilePanel(psiFile)));
  }

  public static void asyncAnalyseProjectAndUpdatePanel(@NotNull Project project) {
    ProgressManager.getInstance()
        .run(
            new Task.Backgroundable(project, "Analysing all project files...") {
              @Override
              public void run(@NotNull ProgressIndicator indicator) {
                ApplicationManager.getApplication().runReadAction(doUpdate(project));
                //        ServiceManager.getService(project, myTodoView.class).refresh();
              }
            });
    //    ReadAction.nonBlocking(doUpdate(project)).submit(NonUrgentExecutor.getInstance());
  }

  @NotNull
  private static Runnable doUpdate(Project project) {
    return () -> {
      AnalysisData.getAnalysis(DeepCodeUtils.getAllSupportedFilesInProject(project));
      ServiceManager.getService(project, myTodoView.class).refresh();
      //      StatusBarUtil.setStatusBarInfo(project, message);
    };
  }

  public static List<PsiFile> getAllSupportedFilesInProject(@NotNull Project project) {
    return allProjectFiles(project).stream()
        .filter(DeepCodeUtils::isSupportedFileFormat)
        .collect(Collectors.toList());
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
      for (AnalysisData.SuggestionForFile suggestion : AnalysisData.getAnalysis(file)) {
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

  /** Potentially <b>Heavy</b> network request! */
  public static boolean isNotLogged(@Nullable Project project) {
    final String sessionToken = DeepCodeParams.getSessionToken();
    boolean isNotLogged =
        sessionToken.isEmpty() || DeepCodeRestApi.checkSession(sessionToken).getStatusCode() != 200;
    if (isNotLogged) {
      // fixme debug only
      System.out.println("Logging check fails!");

      // new logging was not requested during current session of withing previous IDE start.
      if (!DeepCodeParams.loggingRequested && sessionToken.isEmpty()) {
        requestNewLogin(project);
      }
    } else {
      // fixme debug only
      System.out.println("Logging check succeed.");
    }
    return isNotLogged;
  }

  private static boolean wasErrorShown = false;

  private static void requestNewLogin(@Nullable Project project) {
    DeepCodeParams.clearLoginParams();
    LoginResponse response = DeepCodeRestApi.newLogin();
    if (response.getStatusCode() == 200) {
      setSessionToken(response.getSessionToken());
      setLoginUrl(response.getLoginURL());
      DeepCodeNotifications.showLoginLink(project);
      DeepCodeParams.loggingRequested = true;
      wasErrorShown = false;
    } else {
      if (!wasErrorShown) {
        DeepCodeNotifications.showError(response.getStatusDescription(), project);
      }
      wasErrorShown = true;
    }
  }

  public static boolean isSupportedFileFormat(PsiFile psiFile) {
    String fileExtension = psiFile.getVirtualFile().getExtension();
    return getSupportedExtensions(psiFile.getProject()).contains(fileExtension);
  }

  /** Potentially <b>Heavy</b> network request! */
  private static Set<String> getSupportedExtensions(Project project) {
    if (supportedExtensions.isEmpty()) {
      GetFiltersResponse filtersResponse =
          DeepCodeRestApi.getFilters(DeepCodeParams.getSessionToken());
      if (filtersResponse.getStatusCode() == 200) {
        supportedExtensions =
            filtersResponse.getExtensions().stream()
                .map(s -> s.substring(1)) // remove preceding `.` (`.js` -> `js`)
                .collect(Collectors.toSet());
        // fixme debug only
        System.out.println(supportedExtensions);
        /*
              } else if (filtersResponse.getStatusCode() == 401) {
                DeepCodeNotifications.showLoginLink(project);
        */
      } else {
        // fixme debug only
        System.out.println(
            "Can't retrieve supported file extensions list from the server. Fallback to default set.\n"
                + filtersResponse.getStatusCode() + " " + filtersResponse.getStatusDescription());
        supportedExtensions =
            new HashSet<>(
                Arrays.asList(
                    "cc", "htm", "cpp", "c", "vue", "h", "hpp", "es6", "js", "py", "es", "jsx",
                    "java", "tsx", "html", "ts"));
      }
    }
    return supportedExtensions;
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
