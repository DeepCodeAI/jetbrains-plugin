package ai.deepcode.jbplugin;

import ai.deepcode.jbplugin.core.AnalysisData;
import ai.deepcode.jbplugin.ui.myTodoView;
import ai.deepcode.jbplugin.core.DeepCodeUtils;
import ai.deepcode.jbplugin.core.MyBulkFileListener;
import ai.deepcode.jbplugin.core.MyProjectManagerListener;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

public class DeepCodeStartupActivity implements StartupActivity {

  private static boolean listenersActivated = false;

  @Override
  public void runActivity(@NotNull Project project) {
    if (!listenersActivated) {
      final MessageBusConnection messageBusConnection =
          ApplicationManager.getApplication().getMessageBus().connect();
      messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, new MyBulkFileListener());
      messageBusConnection.subscribe(ProjectManager.TOPIC, new MyProjectManagerListener(project));
      listenersActivated = true;
    }
    // Keep commented - for DEBUG ONLY !!!!!!!!!!!!!!!!!
    //PropertiesComponent.getInstance(project).setValue("consentGiven", false);

    AnalysisData.clearCache(project);
    // Initial logging if needed.
    if (DeepCodeUtils.isLogged(project, true)) {
      DeepCodeUtils.asyncAnalyseProjectAndUpdatePanel(project);
    }
    /*
        // Update CurrentFile Panel if file Tab was changed in Editor
        MessageBusConnection connection = project.getMessageBus().connect();
        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new MyEditorManagerListener());
    */
  }

  private static final class MyEditorManagerListener implements FileEditorManagerListener {

    @Override
    public void selectionChanged(@NotNull final FileEditorManagerEvent event) {
      final VirtualFile virtualFile = event.getNewFile();
      if (virtualFile == null) return;
      final Project project = event.getManager().getProject();
      final PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
      //      System.out.println(virtualFile);
      DeepCodeUtils.asyncUpdateCurrentFilePanel(psiFile);
      ServiceManager.getService(project, myTodoView.class).refresh();
    }
  }
}
