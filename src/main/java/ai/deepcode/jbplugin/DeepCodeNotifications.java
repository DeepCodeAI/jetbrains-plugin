package ai.deepcode.jbplugin;

import ai.deepcode.jbplugin.core.DeepCodeParams;
import ai.deepcode.jbplugin.core.DeepCodeUtils;
import com.intellij.ide.BrowserUtil;
import com.intellij.notification.*;
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
  static final String groupNeedAction = "DeepCodeNeedAction";
  static final String groupAutoHide = "DeepCodeAutoHide";
  static Runnable lastNotificationRunnable = () -> {};
  static final List<Notification> lastNotifications = new ArrayList<>();

  static {
    new NotificationGroup(
        groupNeedAction, NotificationDisplayType.STICKY_BALLOON, true, "DeepCode");
  }

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
    lastNotifications.forEach(DeepCodeNotifications::expireNotification);
    lastNotifications.clear();
    for (Project prj : projects) {
      final Notification notification =
          new Notification(groupNeedAction, title, message, NotificationType.WARNING)
              .addAction(
                  new ShowClickableLinkAction(
                      "Login", () -> DeepCodeUtils.requestNewLogin(prj), true));
      lastNotifications.add(notification);
      notification.notify(prj);
    }
  }

  private static boolean consentRequestShown = false;

  public static void showConsentRequest(@NotNull Project project, boolean userActionNeeded) {
    if (!userActionNeeded && consentRequestShown) return;
    lastNotificationRunnable = () -> showConsentRequest(project, userActionNeeded);
    final String message =
        "Confirm remote analysis of "
            + project.getBasePath();
//            + " (<a href=\"https://www.deepcode.ai/tc\">Terms & Conditions</a>)";
    final Notification notification =
        new ConsentNotification(
                groupNeedAction,
                title + ": Confirm remote analysis of",
                project.getName(),
                NotificationType.WARNING,
                null /*NotificationListener.URL_OPENING_LISTENER*/)
            .addAction(
                new ShowClickableLinkAction(
                    "CONFIRM",
                    () -> {
                      DeepCodeParams.setConsentGiven(project);
                      consentRequestShown = false;
                      DeepCodeUtils.asyncAnalyseProjectAndUpdatePanel(project);
                    },
                    true))
            .addAction(
                new ShowClickableLinkAction(
                    "Terms and Conditions",
                    () -> BrowserUtil.open("https://www.deepcode.ai/tc"),
                    false));
    lastNotifications.forEach(DeepCodeNotifications::expireNotification);
    lastNotifications.clear();
    lastNotifications.add(notification);
    notification.notify(project);
    consentRequestShown = true;
  }

  private static class ShowClickableLinkAction extends DumbAwareAction {

    private final Runnable onClickRunnable;
    private final boolean expiredIfClicked;

    ShowClickableLinkAction(
        @NotNull String linkText, @NotNull Runnable onClickRunnable, boolean expiredIfClicked) {
      super(linkText);
      this.onClickRunnable = onClickRunnable;
      this.expiredIfClicked = expiredIfClicked;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      if (expiredIfClicked) expireNotification(Notification.get(e));
      onClickRunnable.run();
    }
  }

  private static void expireNotification(@NotNull Notification notification){
    if (notification instanceof ConsentNotification) {
      ((ConsentNotification) notification).consentExpired();
    } else {
      notification.expire();
    }
  }

  private static class ConsentNotification extends Notification {
    public ConsentNotification(
        @NotNull String groupDisplayId,
        @NotNull String title,
        @NotNull String content,
        @NotNull NotificationType type,
        @Nullable NotificationListener listener) {
      super(groupDisplayId, title, content, type, listener);
    }

    @Override
    public void expire() {
      // super.expire();
    }

    public void consentExpired() {
      super.expire();
      consentRequestShown = false;
    }
  }

  public static void showError(@NotNull String message, @NotNull Project project) {
    lastNotificationRunnable = () -> showError(message, project);
    new Notification(groupAutoHide, title, message, NotificationType.ERROR).notify(project);
  }

  public static void showInfo(@NotNull String message, @NotNull Project project) {
    lastNotificationRunnable = () -> showInfo(message, project);
    new Notification(groupAutoHide, title, message, NotificationType.INFORMATION).notify(project);
  }

  public static void showWarn(@NotNull String message, @NotNull Project project) {
    lastNotificationRunnable = () -> showWarn(message, project);
    new Notification(groupAutoHide, title, message, NotificationType.WARNING).notify(project);
  }
}
