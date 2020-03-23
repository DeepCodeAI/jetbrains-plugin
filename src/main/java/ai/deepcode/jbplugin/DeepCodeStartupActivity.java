package ai.deepcode.jbplugin;

import ai.deepcode.javaclient.DeepCodeRestApi;
import ai.deepcode.javaclient.responses.LoginResponse;
import ai.deepcode.jbplugin.utils.DeepCodeParams;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

import static ai.deepcode.jbplugin.utils.DeepCodeParams.setLoginUrl;
import static ai.deepcode.jbplugin.utils.DeepCodeParams.setSessionToken;

public class DeepCodeStartupActivity implements StartupActivity {

  @Override
  public void runActivity(@NotNull Project project) {
    if (!DeepCodeParams.isLogged()) {
      LoginResponse response = DeepCodeRestApi.newLogin();
      if (response.getStatusCode() == 200) {
        setSessionToken(response.getSessionToken());
        setLoginUrl(response.getLoginURL());
        DeepCodeNotifications.showLoginLink(project);
      } else {
        DeepCodeNotifications.showError(response.getStatusDescription(), project);
      }
    }
  }
}
