package ai.deepcode.jbplugin;

import ai.deepcode.jbplugin.utils.DeepCodeParams;
import com.intellij.ide.BrowserUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static ai.deepcode.jbplugin.utils.DeepCodeParams.getLoginUrl;

public class DeepCodeNotifications {

  static final String title = "DeepCode";
  static final String groupDisplayId = "DeepCode";

  public static void showLoginLink(@Nullable Project project) {
    DeepCodeParams.loggingRequested = true;
    new Notification(
            groupDisplayId,
            title,
            "Login to your DeepCode account, please: ",
            NotificationType.INFORMATION)
        .addAction(new ShowLoginAction())
        .notify(project);
  }

  private static class ShowLoginAction extends DumbAwareAction {

    ShowLoginAction() {
      super(getLoginUrl());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      BrowserUtil.open(getLoginUrl());
    }
  }

  public static void showError(String message, Project project) {
    new Notification(groupDisplayId, title, message, NotificationType.ERROR).notify(project);
  }

  public static void showInfo(String message, Project project) {
    new Notification(groupDisplayId, title, message, NotificationType.INFORMATION).notify(project);
  }

  public static void showWarn(String message, Project project) {
    new Notification(groupDisplayId, title, message, NotificationType.WARNING).notify(project);
  }
}
