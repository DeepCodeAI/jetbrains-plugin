package ai.deepcode.jbplugin.utils;

import ai.deepcode.jbplugin.DeepCodeToolWindowFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

public final class DeepCodeUtils {
  private DeepCodeUtils() {}

  public static void asyncUpdateCurrentFilePanel(PsiFile psiFile) {
    ApplicationManager.getApplication()
        .invokeLater(
            () ->
                WriteCommandAction.runWriteCommandAction(
                    psiFile.getProject(),
                    () -> DeepCodeToolWindowFactory.updateCurrentFilePanel(psiFile)));
  }

  public static List<PsiFile> getAllSupportedFilesInProject(@NotNull Project project) {
    return PsiUtilCore.toPsiFiles(
            PsiManager.getInstance(project),
            // fixme
            FileTypeIndex.getFiles(PlainTextFileType.INSTANCE, GlobalSearchScope.allScope(project)))
        .stream()
        .filter(DeepCodeParams::isSupportedFileFormat)
        .collect(Collectors.toList());
  }
}
