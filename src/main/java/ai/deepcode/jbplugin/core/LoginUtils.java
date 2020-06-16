package ai.deepcode.jbplugin.core;

import ai.deepcode.javaclient.DeepCodeRestApi;
import ai.deepcode.javaclient.responses.EmptyResponse;
import ai.deepcode.javaclient.responses.LoginResponse;
import ai.deepcode.jbplugin.DeepCodeNotifications;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.NonUrgentExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LoginUtils {
  private LoginUtils() {}

  private static final String userAgent =
      "JetBrains-"
          + ApplicationNamesInfo.getInstance().getProductName()
          + "-"
          + ApplicationInfo.getInstance().getFullVersion();

  private static boolean isLoginCheckLoopStarted = false;

  /** network request! */
  public static boolean isLogged(@Nullable Project project, boolean userActionNeeded) {
    final String sessionToken = DeepCodeParams.getInstance().getSessionToken();
    ProgressManager.checkCanceled();
    final EmptyResponse response = DeepCodeRestApi.checkSession(sessionToken);
    boolean isLogged = response.getStatusCode() == 200;
    String message = response.getStatusDescription();
    if (isLogged) {
      DCLogger.getInstance().logInfo("Login check succeed." + " Token: " + sessionToken);
    } else {
      DCLogger.getInstance().logWarn("Login check fails: " + message + " Token: " + sessionToken);
    }
    if (!isLogged && userActionNeeded) {
      if (sessionToken.isEmpty() && response.getStatusCode() == 401) {
        message = "Authenticate using your GitHub, Bitbucket or GitLab account";
      }
      DeepCodeNotifications.showLoginLink(project, message);
    } else if (isLogged && project != null) {
      if (DeepCodeParams.getInstance().consentGiven(project)) {
        DCLogger.getInstance().logInfo("Consent check succeed for: " + project);
      } else {
        DCLogger.getInstance().logWarn("Consent check fail! Project: " + project.getName());
        isLogged = false;
        DeepCodeNotifications.showConsentRequest(project, userActionNeeded);
      }
    }
    return isLogged;
  }

  /** network request! */
  public static void requestNewLogin(@NotNull Project project, boolean openBrowser) {
    DCLogger.getInstance().logInfo("New Login requested.");
    DeepCodeParams.getInstance().clearLoginParams();
    LoginResponse response = DeepCodeRestApi.newLogin(userAgent);
    if (response.getStatusCode() == 200) {
      DCLogger.getInstance().logInfo("New Login request succeed. New Token: " + response.getSessionToken());
      DeepCodeParams.getInstance().setSessionToken(response.getSessionToken());
      DeepCodeParams.getInstance().setLoginUrl(response.getLoginURL());
      if (openBrowser) {
        BrowserUtil.open(DeepCodeParams.getInstance().getLoginUrl());
      }
      if (!isLoginCheckLoopStarted) {
        ReadAction.nonBlocking(() -> startLoginCheckLoop(project))
            .submit(NonUrgentExecutor.getInstance());
      }
    } else {
      DCLogger.getInstance().logWarn("New Login request fail: " + response.getStatusDescription());
      DeepCodeNotifications.showError(response.getStatusDescription(), project);
    }
  }

  private static void startLoginCheckLoop(@NotNull Project project) {
    isLoginCheckLoopStarted = true;
    do {
      RunUtils.delay(RunUtils.DEFAULT_DELAY);
    } while (!isLogged(project, false));
    isLoginCheckLoopStarted = false;
    DeepCodeNotifications.showInfo("Login succeed", project);
    AnalysisData.getInstance().resetCachesAndTasks(project);
    RunUtils.asyncAnalyseProjectAndUpdatePanel(project);
  }
}
