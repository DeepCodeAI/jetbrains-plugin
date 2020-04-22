package ai.deepcode.jbplugin.utils;

import ai.deepcode.javaclient.DeepCodeRestApi;
import ai.deepcode.javaclient.responses.GetFiltersResponse;
import ai.deepcode.javaclient.responses.LoginResponse;
import ai.deepcode.jbplugin.DeepCodeNotifications;
import ai.deepcode.jbplugin.DeepCodeConsoleToolWindowFactory;
import ai.deepcode.jbplugin.ui.myTodoView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import static ai.deepcode.jbplugin.utils.DeepCodeParams.setLoginUrl;
import static ai.deepcode.jbplugin.utils.DeepCodeParams.setSessionToken;

public final class DeepCodeUtils {
  private static Set<String> supportedExtensions = Collections.emptySet();
  private static final SimpleDateFormat HMSS = new SimpleDateFormat("h:m:s,S");

  private DeepCodeUtils() {}

  public static void logDeepCode(String message) {
    String currentTime = "[" + HMSS.format(System.currentTimeMillis()) + "] ";
    if (message.length() > 500) {
      message =
          message.substring(0, 500) + " ... [" + (message.length() - 500) + " more symbols were cut]";
    }
    // fixme: made DeepCode console
    System.out.println(currentTime + message);
  }

  public static void asyncUpdateCurrentFilePanel(PsiFile psiFile) {
    ApplicationManager.getApplication()
        .invokeLater(
            () ->
                WriteCommandAction.runWriteCommandAction(
                    psiFile.getProject(),
                    () -> DeepCodeConsoleToolWindowFactory.updateCurrentFilePanel(psiFile)));
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
    boolean result;
    final String sessionToken = DeepCodeParams.getSessionToken();
    if (sessionToken.isEmpty()) {
      requestNewLogin(project);
      result = true;
    } else {
      final int statusCode = DeepCodeRestApi.checkSession(sessionToken).getStatusCode();
      if (statusCode == 401) {
        requestNewLogin(project);
        result = true;
      } else if (statusCode == 304) {
        if (!DeepCodeParams.loggingRequested) {
          DeepCodeNotifications.showLoginLink(project);
          DeepCodeParams.loggingRequested = true;
        }
        result = true;
      } else result = statusCode != 200;
    }
    logDeepCode(
        ((result) ? "Logging check fails!" : "Logging check succeed.") + " Token: " + sessionToken);
    return result;
  }

  public static void requestNewLogin(@Nullable Project project) {
    Project[] projects =
        (project != null)
            ? new Project[] {project}
            : ProjectManager.getInstance().getOpenProjects();
    DeepCodeParams.clearLoginParams();
    LoginResponse response = DeepCodeRestApi.newLogin();
    if (response.getStatusCode() == 200) {
      setSessionToken(response.getSessionToken());
      setLoginUrl(response.getLoginURL());
      for (Project prj : projects) {
        DeepCodeNotifications.showLoginLink(prj);
      }
      DeepCodeParams.loggingRequested = true;
    } else {
      for (Project prj : projects) {
        DeepCodeNotifications.showError(response.getStatusDescription(), prj);
      }
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
        logDeepCode("Supported extensions: " + supportedExtensions);
        /*
              } else if (filtersResponse.getStatusCode() == 401) {
                DeepCodeNotifications.showLoginLink(project);
        */
      } else {
        logDeepCode(
            "Can't retrieve supported file extensions list from the server. Fallback to default set.\n"
                + filtersResponse.getStatusCode()
                + " "
                + filtersResponse.getStatusDescription());
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
