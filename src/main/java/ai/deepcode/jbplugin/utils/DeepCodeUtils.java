package ai.deepcode.jbplugin.utils;

import ai.deepcode.jbplugin.DeepCodeToolWindowFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    return allProjectFiles(project)
        .stream()
        .filter(DeepCodeParams::isSupportedFileFormat)
        .collect(Collectors.toList());
  }

  private static List<PsiFile> allProjectFiles(@NotNull Project project) {
    final PsiManager psiManager = PsiManager.getInstance(project);
    final PsiDirectory prjDirectory = psiManager.findDirectory(project.getBaseDir());
    return prjDirectory != null ? getFilesRecursively(prjDirectory) : Collections.emptyList();
  }

  private static List<PsiFile> getFilesRecursively(@NotNull PsiDirectory psiDirectory) {
    List<PsiFile> psiFileList = new ArrayList<>(Arrays.asList(psiDirectory.getFiles()));
    for (PsiDirectory subDir : psiDirectory.getSubdirectories()) {
      psiFileList.addAll(getFilesRecursively(subDir));
    }
    return psiFileList;
  }
}
