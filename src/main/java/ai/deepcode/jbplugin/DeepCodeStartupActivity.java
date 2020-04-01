package ai.deepcode.jbplugin;

import ai.deepcode.javaclient.DeepCodeRestApi;
import ai.deepcode.javaclient.responses.LoginResponse;
import ai.deepcode.jbplugin.utils.DeepCodeParams;
import ai.deepcode.jbplugin.utils.DeepCodeUtils;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.messages.MessageBusConnection;
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
    // Update CurrentFile Panel if file Tab was changed in Editor
    MessageBusConnection connection = project.getMessageBus().connect();
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new MyEditorManagerListener());
  }

  private static final class MyEditorManagerListener implements FileEditorManagerListener {

    @Override
    public void selectionChanged(@NotNull final FileEditorManagerEvent event){
      final VirtualFile virtualFile = event.getNewFile();
      final PsiFile psiFile = PsiManager.getInstance(event.getManager().getProject()).findFile(virtualFile);
//      System.out.println(virtualFile);
      DeepCodeUtils.asyncUpdateCurrentFilePanel(psiFile);
    }
  }
}
