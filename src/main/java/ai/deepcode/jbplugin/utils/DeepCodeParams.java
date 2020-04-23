package ai.deepcode.jbplugin.utils;

import ai.deepcode.javaclient.DeepCodeRestApi;
import ai.deepcode.javaclient.responses.GetFiltersResponse;
import ai.deepcode.jbplugin.DeepCodeNotifications;
import ai.deepcode.jbplugin.ui.myTodoView;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public class DeepCodeParams {

  // Settings
  private static boolean isEnable;
  private static String apiUrl;
  private static boolean useLinter;
  private static int minSeverity;
  private static String sessionToken;

  // Inner params
  private static String loginUrl;
  public static boolean loggingRequested = false; // indicate that new login was requested withing current session

  // TODO https://www.jetbrains.org/intellij/sdk/docs/basics/persisting_sensitive_data.html
  private static final PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();

  private DeepCodeParams() {}

  public static void clearLoginParams(){
    setSessionToken("");
    setLoginUrl("");
    loggingRequested = false;
  }

  @NotNull
  public static String getSessionToken() {
    return sessionToken;
  }

  public static void setSessionToken(String sessionToken) {
    DeepCodeParams.sessionToken = sessionToken;
    propertiesComponent.setValue("sessionToken", sessionToken);
    loggingRequested = false;
  }

  @NotNull
  public static String getLoginUrl() {
    return loginUrl;
  }

  public static void setLoginUrl(String loginUrl) {
    DeepCodeParams.loginUrl = loginUrl;
    propertiesComponent.setValue("loginUrl", loginUrl);
    loggingRequested = false;
  }

  public static boolean useLinter() {
    return useLinter;
  }

  public static void setUseLinter(boolean useLinter) {
    DeepCodeParams.useLinter = useLinter;
    propertiesComponent.setValue("useLinter", useLinter);
  }

  public static int getMinSeverity() {
    return minSeverity;
  }

  public static void setMinSeverity(int minSeverity) {
    DeepCodeParams.minSeverity = minSeverity;
    propertiesComponent.setValue("minSeverity", String.valueOf(minSeverity));
  }

  @NotNull
  public static String getApiUrl() {
    return apiUrl;
  }

  public static void setApiUrl(String apiUrl) {
//    if (apiUrl == null || apiUrl.isEmpty()) apiUrl = "https://www.deepcode.ai/";
    if (!apiUrl.endsWith("/")) apiUrl += "/";
    if (apiUrl.equals(DeepCodeParams.apiUrl)) return;
    DeepCodeParams.apiUrl = apiUrl;
    propertiesComponent.setValue("apiUrl", apiUrl);
    DeepCodeRestApi.setBaseUrl(apiUrl);
    clearLoginParams();
    AnalysisData.clearCache(null);
  }

  public static boolean isEnable() {
    return isEnable;
  }

  public static void setEnable(boolean isEnable) {
    DeepCodeParams.isEnable = isEnable;
    propertiesComponent.setValue("isEnable", isEnable);
  }

  static {
    isEnable = propertiesComponent.getBoolean("isEnable", true);
    apiUrl = propertiesComponent.getValue("apiUrl", "");
    DeepCodeRestApi.setBaseUrl(apiUrl);
    sessionToken = propertiesComponent.getValue("sessionToken", "");
    loginUrl = propertiesComponent.getValue("loginUrl", "");
    useLinter = propertiesComponent.getBoolean("useLinter", false);
    minSeverity = propertiesComponent.getInt("minSeverity", 1);
  }

}
