package ai.deepcode.jbplugin;

import ai.deepcode.jbplugin.core.DeepCodeParams;
import ai.deepcode.jbplugin.core.LoginUtils;
import ai.deepcode.jbplugin.core.RunUtils;
import com.intellij.ide.BrowserUtil;
import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DeepCodeNotifications {

  static final String title = "DeepCode";
  static final String groupNeedAction = "DeepCodeNeedAction";
  static final String groupAutoHide = "DeepCodeAutoHide";

  static {
    new NotificationGroup(
        groupNeedAction, NotificationDisplayType.STICKY_BALLOON, true, "DeepCode");
  }

  public static void showLoginLink(@Nullable Project project, @NotNull String message) {
    // application wide notifications (with project=null) are not shown
    // https://youtrack.jetbrains.com/issue/IDEA-220408
    Project[] projects =
        (project != null)
            ? new Project[] {project}
            : ProjectManager.getInstance().getOpenProjects();
    List<Notification> notificationsToExpireWith = new ArrayList<>();
    for (Project prj : projects) {
      final Notification notification =
          new Notification(groupNeedAction, title, message, NotificationType.WARNING)
              .addAction(
                  new ShowClickableLinkAction(
                      "Login",
                      () -> LoginUtils.getInstance().requestNewLogin(prj, true),
                      true,
                      notificationsToExpireWith));
      notificationsToExpireWith.add(notification);
      notification.notify(prj);
    }
  }

  private static boolean consentRequestShown = false;

  public static void showConsentRequest(@NotNull Project project, boolean userActionNeeded) {
    if (!userActionNeeded && consentRequestShown) return;
    final String message = "Confirm remote analysis of " + project.getBasePath();
    //            + " (<a href=\"https://www.deepcode.ai/tc\">Terms & Conditions</a>)";
    final Notification notification =
        new ConsentNotification(
                groupNeedAction,
                title + ": Confirm remote analysis of ",
                project.getName(),
                NotificationType.WARNING,
                null /*NotificationListener.URL_OPENING_LISTENER*/)
            .addAction(
                new ShowClickableLinkAction(
                    "CONFIRM",
                    () -> {
                      DeepCodeParams.getInstance().setConsentGiven(project);
                      consentRequestShown = false;
                      RunUtils.getInstance().asyncAnalyseProjectAndUpdatePanel(project);
                    },
                    true,
                    Collections.emptyList()))
            .addAction(
                new ShowClickableLinkAction(
                    "Terms and Conditions",
                    () -> BrowserUtil.open("https://www.deepcode.ai/tc"),
                    false,
                    Collections.emptyList()));
    notification.notify(project);
    consentRequestShown = true;
  }

  private static class ShowClickableLinkAction extends DumbAwareAction {

    private final Runnable onClickRunnable;
    private final boolean expiredIfClicked;
    private final List<Notification> notificationsToExpireWith;

    ShowClickableLinkAction(
        @NotNull String linkText,
        @NotNull Runnable onClickRunnable,
        boolean expiredIfClicked,
        List<Notification> notificationsToExpireWith) {
      super(linkText);
      this.onClickRunnable = onClickRunnable;
      this.expiredIfClicked = expiredIfClicked;
      this.notificationsToExpireWith = notificationsToExpireWith;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      if (expiredIfClicked) {
        notificationsToExpireWith.forEach(DeepCodeNotifications::expireNotification);
        expireNotification(Notification.get(e));
      }
      onClickRunnable.run();
    }
  }

  private static void expireNotification(@NotNull Notification notification) {
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
    new Notification(groupAutoHide, title, message, NotificationType.ERROR).notify(project);
  }

  public static void showInfo(@NotNull String message, @NotNull Project project) {
    new Notification(groupAutoHide, title, message, NotificationType.INFORMATION).notify(project);
  }

  public static void showWarn(@NotNull String message, @NotNull Project project) {
    new Notification(groupAutoHide, title, message, NotificationType.WARNING).notify(project);
  }
}
