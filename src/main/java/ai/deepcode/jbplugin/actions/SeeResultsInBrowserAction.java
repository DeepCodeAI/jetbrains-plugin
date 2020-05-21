package ai.deepcode.jbplugin.actions;

import ai.deepcode.jbplugin.core.AnalysisData;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public class SeeResultsInBrowserAction extends AnAction {

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(!AnalysisData.getAnalysisUrl().isEmpty());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final String analysisUrl = AnalysisData.getAnalysisUrl();
    if (!analysisUrl.isEmpty()) BrowserUtil.open(analysisUrl);
  }
}
