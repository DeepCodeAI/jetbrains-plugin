package ai.deepcode.jbplugin;

import ai.deepcode.jbplugin.core.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

public class DeepCodeStartupActivity implements StartupActivity {

  private static boolean listenersActivated = false;

  @Override
  public void runActivity(@NotNull Project project) {
    if (DeepCodeParams.getInstance().isPluginDeprecated()) {
      DeepCodeNotifications.showPluginDeprecationAnnouncement(project);
      // Unfortunately, we can't disable plugin here programmatically: https://intellij-support.jetbrains.com/hc/en-us/community/posts/360009820960/comments/360002292200
      // as workaround will return `isLogged() = false` to stop requesting disabled API
    }

    if (!listenersActivated) {
      final MessageBusConnection messageBusConnection =
          ApplicationManager.getApplication().getMessageBus().connect();
      messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, new MyBulkFileListener());
      messageBusConnection.subscribe(ProjectManager.TOPIC, new MyProjectManagerListener(project));
      listenersActivated = true;
    }
    if (DeepCodeParams.getInstance().isFirstStart()) {
      DeepCodeNotifications.showTutorialRequest(project);
    }
    if (DeepCodeParams.getInstance().needToShowReplacementMessage()) {
      DeepCodeNotifications.showPluginReplacementAnnouncement(project);
    }
    // Keep commented - for DEBUG ONLY !!!!!!!!!!!!!!!!!
    //PropertiesComponent.getInstance(project).setValue("consentGiven", false);

    AnalysisData.getInstance().resetCachesAndTasks(project);
    // Initial logging if needed.
    if (LoginUtils.getInstance().isLogged(project, true)) {
      RunUtils.getInstance().asyncAnalyseProjectAndUpdatePanel(project);
    }
    // Keep commented - for DEBUG ONLY !!!!!!!!!!!!!!!!!
    //throw new NullPointerException();

    /*
        // Update CurrentFile Panel if file Tab was changed in Editor
        MessageBusConnection connection = project.getMessageBus().connect();
        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new MyEditorManagerListener());
    */
  }
}
