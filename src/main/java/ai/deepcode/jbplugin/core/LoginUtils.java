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
    final String sessionToken = DeepCodeParams.getSessionToken();
    ProgressManager.checkCanceled();
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
  public static void requestNewLogin(@NotNull Project project, boolean openBrowser) {
    DCLogger.info("New Login requested.");
    DeepCodeParams.clearLoginParams();
    LoginResponse response = DeepCodeRestApi.newLogin(userAgent);
    if (response.getStatusCode() == 200) {
      DCLogger.info("New Login request succeed. New Token: " + response.getSessionToken());
      DeepCodeParams.setSessionToken(response.getSessionToken());
      DeepCodeParams.setLoginUrl(response.getLoginURL());
      if (openBrowser) {
        BrowserUtil.open(DeepCodeParams.getLoginUrl());
      }
      if (!isLoginCheckLoopStarted) {
        ReadAction.nonBlocking(() -> startLoginCheckLoop(project))
            .submit(NonUrgentExecutor.getInstance());
      }
    } else {
      DCLogger.warn("New Login request fail: " + response.getStatusDescription());
      DeepCodeNotifications.showError(response.getStatusDescription(), project);
    }
  }

  private static void startLoginCheckLoop(@NotNull Project project) {
    isLoginCheckLoopStarted = true;
    do {
      RunUtils.delay(1000);
    } while (!isLogged(project, false));
    isLoginCheckLoopStarted = false;
    DeepCodeNotifications.showInfo("Login succeed", project);
    AnalysisData.clearCache(project);
    RunUtils.asyncAnalyseProjectAndUpdatePanel(project);
  }
}
