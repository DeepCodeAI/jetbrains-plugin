package ai.deepcode.jbplugin;

import ai.deepcode.jbplugin.utils.DeepCodeUtils;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DeepCodeNotifications {

  static final String title = "DeepCode";
  static final String groupDisplayId = "DeepCode";
  static Runnable lastNotificationRunnable = () -> {};
  static final List<Notification> lastNotifications = new ArrayList<>();

  public static void reShowLastNotification() {
    lastNotificationRunnable.run();
  }

  public static void showLoginLink(@Nullable Project project, @NotNull String message) {
    lastNotificationRunnable = () -> showLoginLink(project, message);
    // application wide notifications (with project=null) are not shown
    // https://youtrack.jetbrains.com/issue/IDEA-220408
    Project[] projects =
        (project != null)
            ? new Project[] {project}
            : ProjectManager.getInstance().getOpenProjects();
    lastNotifications.forEach(Notification::expire);
    lastNotifications.clear();
    for (Project prj : projects) {
      final Notification notification =
          new Notification(groupDisplayId, title, message, NotificationType.WARNING)
              .addAction(new ShowLoginAction(prj));
      lastNotifications.add(notification);
      notification.notify(prj);
    }
  }

  private static class ShowLoginAction extends DumbAwareAction {

    private final Project myProject;

    ShowLoginAction(@NotNull Project project) {
      super("Open web-browser to Login with new token");
      myProject = project;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Notification.get(e).expire();
      DeepCodeUtils.requestNewLogin(myProject);
    }
  }

  public static void showError(@NotNull String message, @NotNull Project project) {
    lastNotificationRunnable = () -> showError(message, project);
    new Notification(groupDisplayId, title, message, NotificationType.ERROR).notify(project);
  }

  public static void showInfo(@NotNull String message, @NotNull Project project) {
    lastNotificationRunnable = () -> showInfo(message, project);
    new Notification(groupDisplayId, title, message, NotificationType.INFORMATION).notify(project);
  }

  public static void showWarn(@NotNull String message, @NotNull Project project) {
    lastNotificationRunnable = () -> showWarn(message, project);
    new Notification(groupDisplayId, title, message, NotificationType.WARNING).notify(project);
  }
}
