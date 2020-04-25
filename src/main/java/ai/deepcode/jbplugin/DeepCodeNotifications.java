package ai.deepcode.jbplugin;

import ai.deepcode.jbplugin.utils.DeepCodeParams;
import ai.deepcode.jbplugin.utils.DeepCodeUtils;
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
  static Runnable lastNotification = () -> {};

  public static void reShowLastNotification(){
    lastNotification.run();
  }

  public static void showLoginLink(@Nullable Project project, @NotNull String message) {
    lastNotification = () -> showLoginLink(project, message);
    new Notification(
            groupDisplayId,
            title,
            message,
            NotificationType.INFORMATION)
        .addAction(new ShowLoginAction(project))
        .notify(project);
  }

  private static class ShowLoginAction extends DumbAwareAction {

    private final Project myProject;

    ShowLoginAction(@Nullable Project project) {
      super("Open web-browser to Login with new token");
      myProject = project;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      DeepCodeUtils.requestNewLogin(myProject);
    }
  }

  public static void showError(String message, Project project) {
    lastNotification = () -> showError(message, project);
    new Notification(groupDisplayId, title, message, NotificationType.ERROR).notify(project);
  }

  public static void showInfo(String message, Project project) {
    lastNotification = () -> showInfo(message, project);
    new Notification(groupDisplayId, title, message, NotificationType.INFORMATION).notify(project);
  }

  public static void showWarn(String message, Project project) {
    lastNotification = () -> showWarn(message, project);
    new Notification(groupDisplayId, title, message, NotificationType.WARNING).notify(project);
  }
}
