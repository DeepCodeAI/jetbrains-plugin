package ai.deepcode.javaclient.core;

import ai.deepcode.javaclient.DeepCodeRestApi;
import ai.deepcode.javaclient.responses.EmptyResponse;
import ai.deepcode.javaclient.responses.LoginResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class LoginUtilsBase {

  private final PlatformDependentUtilsBase pdUtils;
  private final DeepCodeParamsBase deepCodeParams;
  private final AnalysisDataBase analysisData;
  private final DCLoggerBase dcLogger;

  protected LoginUtilsBase(
      @NotNull PlatformDependentUtilsBase platformDependentUtils,
      @NotNull DeepCodeParamsBase deepCodeParams,
      @NotNull AnalysisDataBase analysisData,
      @NotNull DCLoggerBase dcLogger) {
    this.pdUtils = platformDependentUtils;
    this.deepCodeParams = deepCodeParams;
    this.analysisData = analysisData;
    this.dcLogger = dcLogger;
  }

  protected abstract String getUserAgent();

  private static boolean isLoginCheckLoopStarted = false;

  /** network request! */
  public boolean isLogged(@Nullable Object project, boolean userActionNeeded) {
    final String sessionToken = deepCodeParams.getSessionToken();
    pdUtils.progressCheckCanceled();
    final EmptyResponse response = DeepCodeRestApi.checkSession(sessionToken);
    boolean isLogged = response.getStatusCode() == 200;
    String message = response.getStatusDescription();
    if (isLogged) {
      dcLogger.logInfo("Login check succeed." + " Token: " + sessionToken);
    } else {
      dcLogger.logWarn("Login check fails: " + message + " Token: " + sessionToken);
    }
    if (!isLogged && userActionNeeded) {
      if (sessionToken.isEmpty() && response.getStatusCode() == 401) {
        message = "Authenticate using your GitHub, Bitbucket or GitLab account";
      }
      pdUtils.showLoginLink(project, message);
    } else if (isLogged && project != null) {
      if (deepCodeParams.consentGiven(project)) {
        dcLogger.logInfo("Consent check succeed for: " + pdUtils.getProjectName(project));
      } else {
        dcLogger.logWarn("Consent check fail! Project: " + pdUtils.getProjectName(project));
        isLogged = false;
        pdUtils.showConsentRequest(project, userActionNeeded);
      }
    }
    return isLogged;
  }

  /** network request! */
  public void requestNewLogin(@NotNull Object project, boolean openBrowser) {
    dcLogger.logInfo("New Login requested.");
    deepCodeParams.clearLoginParams();
    LoginResponse response = DeepCodeRestApi.newLogin(getUserAgent());
    if (response.getStatusCode() == 200) {
      dcLogger.logInfo("New Login request succeed. New Token: " + response.getSessionToken());
      deepCodeParams.setSessionToken(response.getSessionToken());
      deepCodeParams.setLoginUrl(response.getLoginURL());
      if (openBrowser) {
        pdUtils.showInBrowser(deepCodeParams.getLoginUrl());
        // BrowserUtil.open(DeepCodeParams.getInstance().getLoginUrl());
      }
      if (!isLoginCheckLoopStarted) {
        pdUtils.runInBackground(project, () -> startLoginCheckLoop(project));
        //        ReadAction.nonBlocking(() -> startLoginCheckLoop(project))
        //            .submit(NonUrgentExecutor.getInstance());
        dcLogger.logInfo("LoginCheckLoop started");
      }
    } else {
      dcLogger.logWarn("New Login request fail: " + response.getStatusDescription());
      pdUtils.showError(response.getStatusDescription(), project);
    }
  }

  private void startLoginCheckLoop(@NotNull Object project) {
    isLoginCheckLoopStarted = true;
    try {
      do {
        pdUtils.delay(pdUtils.DEFAULT_DELAY);
      } while (!isLogged(project, false));
    } finally {
      isLoginCheckLoopStarted = false;
      dcLogger.logInfo("LoginCheckLoop finished for project: " + pdUtils.getProjectName(project));
    }
    pdUtils.showInfo("Login succeed", project);
    analysisData.resetCachesAndTasks(project); // do we need it??
    pdUtils.doFullRescan(project);
    //    AnalysisData.getInstance().resetCachesAndTasks(project);
    //    RunUtils.asyncAnalyseProjectAndUpdatePanel(project);
  }
}
