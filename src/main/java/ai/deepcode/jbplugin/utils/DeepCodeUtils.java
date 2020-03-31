package ai.deepcode.jbplugin.utils;

import ai.deepcode.jbplugin.DeepCodeToolWindowFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiFile;

public final class DeepCodeUtils {
  private DeepCodeUtils(){}

  public static void updateCurrentFilePanel(PsiFile psiFile) {
    ApplicationManager.getApplication()
        .invokeLater(
            () ->
                WriteCommandAction.runWriteCommandAction(
                    psiFile.getProject(),
                    () -> DeepCodeToolWindowFactory.updateCurrentFilePanel(psiFile)));
  }
}
