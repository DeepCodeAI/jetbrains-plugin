package ai.deepcode.jbplugin;

import ai.deepcode.javaclient.DeepCodeRestApi;
import ai.deepcode.javaclient.responses.EmptyResponse;
import ai.deepcode.javaclient.responses.LoginResponse;
import com.intellij.ide.BrowserUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class DeepCodeStartupActivity implements StartupActivity {

  private static String sessionToken = "";
  private static String loginUrl = "";

  public static String getSessionToken() {
    try {
      EmptyResponse response = DeepCodeRestApi.checkSession(sessionToken);
      if (response.getStatusCode() != 200) {
        new LoginLinkNotification().notify(null);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    return sessionToken;
  }

  public static void setSessionToken(String sessionToken) {
    DeepCodeStartupActivity.sessionToken = sessionToken;
  }

  public static String getLoginUrl() {
    return loginUrl;
  }

  public static void setLoginUrl(String loginUrl) {
    DeepCodeStartupActivity.loginUrl = loginUrl;
  }

  @Override
  public void runActivity(@NotNull Project project) {
    try {
      LoginResponse response = DeepCodeRestApi.newLogin();
      if (response.getStatusCode() == 200) {
        setSessionToken(response.getSessionToken());
        setLoginUrl(response.getLoginURL());
        new LoginLinkNotification().notify(project);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static class LoginLinkNotification extends Notification {
    LoginLinkNotification() {
      super(
          "DeepCode",
          "DeepCode",
          "Login to your DeepCode account, please: ",
          NotificationType.INFORMATION);
      addAction(new ShowLoginAction());
    }
  }

  private static class ShowLoginAction extends DumbAwareAction {

    ShowLoginAction() {
      super("DeepCode");
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      BrowserUtil.open(getLoginUrl());
    }
  }
}
