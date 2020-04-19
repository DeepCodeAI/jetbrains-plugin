package ai.deepcode.jbplugin;

import ai.deepcode.jbplugin.ui.myTodoView;
import ai.deepcode.jbplugin.utils.DeepCodeUtils;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

public class DeepCodeStartupActivity implements StartupActivity {

  @Override
  public void runActivity(@NotNull Project project) {
    // Initial logging if needed.
    if (DeepCodeUtils.isNotLogged(project)) {
//      DeepCodeNotifications.reShowLastNotification();
    }
    // Fixme Analyse all project files and update project Panel
//    DeepCodeUtils.asyncAnalyseProjectAndUpdatePanel(project);

    // Update CurrentFile Panel if file Tab was changed in Editor
    MessageBusConnection connection = project.getMessageBus().connect();
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new MyEditorManagerListener());
  }

  private static final class MyEditorManagerListener implements FileEditorManagerListener {

    @Override
    public void selectionChanged(@NotNull final FileEditorManagerEvent event){
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
