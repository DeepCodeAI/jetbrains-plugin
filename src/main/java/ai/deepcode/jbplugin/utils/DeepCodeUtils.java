package ai.deepcode.jbplugin.utils;

import ai.deepcode.jbplugin.DeepCodeToolWindowFactory;
import ai.deepcode.jbplugin.ui.myTodoView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.concurrency.NonUrgentExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.*;
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

  public static void asyncAnalyseProjectAndUpdatePanel(@NotNull Project project) {
    ReadAction.nonBlocking(doUpdate(project)).submit(NonUrgentExecutor.getInstance());
  }

  @NotNull
  private static Runnable doUpdate(Project project) {
    return () -> {
      AnalysisData.getAnalysis(DeepCodeUtils.getAllSupportedFilesInProject(project));
      ServiceManager.getService(project, myTodoView.class).refresh();
    };
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

  // todo mapFile2EWI at AnalysisData
  public static ErrorsWarningsInfos getEWI(Collection<PsiFile> psiFiles) {
    int errors = 0;
    int warnings = 0;
    int infos = 0;
    Set<String> countedSuggestions = new HashSet<>();
    for (PsiFile file : psiFiles) {
      for (AnalysisData.SuggestionForFile suggestion : AnalysisData.getAnalysis(file)) {
        if (!countedSuggestions.contains(suggestion.getId())) {
          final int severity = suggestion.getSeverity();
          if (severity == 1) infos += 1;
          else if (severity == 2) warnings += 1;
          else if (severity == 3) errors += 1;
          countedSuggestions.add(suggestion.getId());
        }
      }
    }
    return new ErrorsWarningsInfos(errors, warnings, infos);
  }

  public static class ErrorsWarningsInfos {
    private int errors;
    private int warnings;
    private int infos;

    public ErrorsWarningsInfos(int errors, int warnings, int infos) {
      this.errors = errors;
      this.warnings = warnings;
      this.infos = infos;
    }

    public int getErrors() {
      return errors;
    }

    public int getWarnings() {
      return warnings;
    }

    public int getInfos() {
      return infos;
    }
  }
}
