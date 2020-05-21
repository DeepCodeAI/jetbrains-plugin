package ai.deepcode.jbplugin.actions;

import ai.deepcode.jbplugin.ui.config.DeepCodeConfigEntry;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import org.jetbrains.annotations.NotNull;

public class ShowSettingsAction extends AnAction {

 @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    ShowSettingsUtil.getInstance().showSettingsDialog(e.getProject(), DeepCodeConfigEntry.class);
  }
}
