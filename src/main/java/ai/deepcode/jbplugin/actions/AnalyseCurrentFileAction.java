package ai.deepcode.jbplugin.actions;

import ai.deepcode.jbplugin.DeepCodeNotifications;
import ai.deepcode.jbplugin.ui.myTodoView;
import ai.deepcode.jbplugin.utils.DeepCodeUtils;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO Remove it?
public class AnalyseCurrentFileAction extends AnAction {
  private static final Logger LOG = LoggerFactory.getLogger("DeepCode.AnalyseCurrentFileAction");

  @Override
  public void update(AnActionEvent e) {
    // perform action if and only if EDITOR != null
    boolean enabled = e.getData(CommonDataKeys.EDITOR) != null;
    e.getPresentation().setEnabled(enabled);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final PsiFile psiFile = event.getRequiredData(PlatformDataKeys.PSI_FILE);
    Project project = psiFile.getProject();

    if (DeepCodeUtils.isNotLogged(project)) {
      DeepCodeNotifications.showLoginLink(project);
      return;
    }

    if (!DeepCodeUtils.isSupportedFileFormat(psiFile)) {
      DeepCodeNotifications.showInfo(
              String.format(
                      "Files with `%1$s` extension are not supported yet.",
                      psiFile.getVirtualFile().getExtension()),
              project);
      return;
    }
    DeepCodeUtils.asyncUpdateCurrentFilePanel(psiFile);
    ServiceManager.getService(project, myTodoView.class).refresh();
  }
}
