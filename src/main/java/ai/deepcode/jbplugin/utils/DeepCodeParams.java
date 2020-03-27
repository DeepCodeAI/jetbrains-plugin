package ai.deepcode.jbplugin.utils;

import ai.deepcode.javaclient.DeepCodeRestApi;
import ai.deepcode.javaclient.responses.GetFiltersResponse;
import ai.deepcode.jbplugin.DeepCodeNotifications;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public class DeepCodeParams {

  // ----- Persistent data -----
  private static String sessionToken;
  private static String loginUrl;
  // TODO https://www.jetbrains.org/intellij/sdk/docs/basics/persisting_sensitive_data.html
  private static PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();

  private static Set<String> supportedExtensions = Collections.emptySet();

  public static String getSessionToken() {
    return sessionToken;
  }

  public static void setSessionToken(String sessionToken) {
    DeepCodeParams.sessionToken = sessionToken;
    propertiesComponent.setValue("sessionToken", sessionToken);
  }

  public static String getLoginUrl() {
    return loginUrl;
  }

  public static void setLoginUrl(String loginUrl) {
    DeepCodeParams.loginUrl = loginUrl;
    propertiesComponent.setValue("loginUrl", loginUrl);
  }

  public static boolean isLogged() {
    return (getSessionToken() != null)
        && DeepCodeRestApi.checkSession(getSessionToken()).getStatusCode() == 200;
  }

  public static boolean isSupportedFileFormat(PsiFile psiFile) {
    String fileExtension = psiFile.getVirtualFile().getExtension();
    return getSupportedExtensions(psiFile.getProject()).contains(fileExtension);
  }

  private static Set<String> getSupportedExtensions(Project project) {
    if (supportedExtensions.isEmpty()) {
      GetFiltersResponse filtersResponse = DeepCodeRestApi.getFilters(getSessionToken());
      if (filtersResponse.getStatusCode() == 200) {
        supportedExtensions =
            filtersResponse.getExtensions().stream()
                .map(s -> s.substring(1)) // remove preceding `.` (`.js` -> `js`)
                .collect(Collectors.toSet());
        System.out.println(supportedExtensions);
/*
      } else if (filtersResponse.getStatusCode() == 401) {
        DeepCodeNotifications.showLoginLink(project);
      } else {
        DeepCodeNotifications.showError(filtersResponse.getStatusDescription(), project);
*/
      }
    }
    return supportedExtensions;
  }

  static {
    sessionToken = propertiesComponent.getValue("sessionToken");
    loginUrl = propertiesComponent.getValue("loginUrl");
  }
}
