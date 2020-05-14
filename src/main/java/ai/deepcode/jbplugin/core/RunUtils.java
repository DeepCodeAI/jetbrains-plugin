package ai.deepcode.jbplugin.core;

import ai.deepcode.jbplugin.ui.myTodoView;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public class RunUtils {
  private RunUtils() {}

  public static void asyncUpdateCurrentFilePanel(PsiFile psiFile) {
    /*
        ApplicationManager.getApplication()
            .invokeLater(
                () ->
                    WriteCommandAction.runWriteCommandAction(
                        psiFile.getProject(),
                        () -> DeepCodeConsoleToolWindowFactory.updateCurrentFilePanel(psiFile)));
    */
  }

  public static <T> T computeInReadActionInSmartMode(
      @NotNull Project project, @NotNull final Computable<T> computation) {
    // fixme debug only
    // DCLogger.info("computeInReadActionInSmartMode requested");
    T result = null;
    final DumbService dumbService =
        ReadAction.compute(() -> project.isDisposed() ? null : DumbService.getInstance(project));
    if (dumbService == null) return result;
    result =
        dumbService.runReadActionInSmartMode(
            () -> {
              // fixme debug only
              // DCLogger.info("computeInReadActionInSmartMode actually executing");
              return computation.compute();
            });
    return result;
  }

  public static void delay(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      e.printStackTrace();
      Thread.currentThread().interrupt();
    }
  }

  private static long timeOfLastRescanRequest = 0;
  // BackgroundTaskQueue ??? com.intellij.openapi.wm.ex.StatusBarEx#getBackgroundProcesses ???
  public static void rescanProject(@Nullable Project project, long delayMilliseconds) {
    runInBackground(
        project,
        () -> {
          long timeOfThisRequest = timeOfLastRescanRequest = System.currentTimeMillis();
          delay(delayMilliseconds);
          if (timeOfLastRescanRequest > timeOfThisRequest) return;
          AnalysisData.clearCache(project);
          asyncAnalyseProjectAndUpdatePanel(project);
        });
  }

  public static void runInBackground(@Nullable Project project, @NotNull Runnable runnable) {
    DCLogger.info("runInBackground requested");
    ProgressManager.getInstance()
        .run(
            new Task.Backgroundable(project, "DeepCode: Analysing Files...") {
              @Override
              public void run(@NotNull ProgressIndicator indicator) {
                runnable.run();
              }
            });
  }

  private static long timeOfLastRunInBackgroundRequest = 0;
  private static ProgressIndicator prevProgressIndicator = null;

  public static void runInBackgroundDelayedCancellable(
      @Nullable Project project, @NotNull Runnable runnable, long delayMilliseconds) {
    DCLogger.info("runInBackgroundDelayedCancellable requested");
    if (prevProgressIndicator != null) {
      prevProgressIndicator.cancel();
      prevProgressIndicator = null;
    }
    ProgressManager.getInstance()
        .run(
            new Task.Backgroundable(project, "DeepCode: Analysing Files...") {
              @Override
              public void run(@NotNull ProgressIndicator indicator) {
                long timeOfThisRequest =
                    timeOfLastRunInBackgroundRequest = System.currentTimeMillis();
                delay(delayMilliseconds);
                if (timeOfLastRunInBackgroundRequest > timeOfThisRequest) return;
                prevProgressIndicator = indicator;
                runnable.run();
              }
            });
  }

  public static void asyncAnalyseProjectAndUpdatePanel(@Nullable Project project) {
    if (project == null) {
      for (Project prj : ProjectManager.getInstance().getOpenProjects()) {
        asyncAnalyseAndUpdatePanel(prj, null);
      }
    } else asyncAnalyseAndUpdatePanel(project, null);
  }

  public static void asyncAnalyseAndUpdatePanel(
      @NotNull Project project, @Nullable Collection<PsiFile> psiFiles) {
    asyncAnalyseAndUpdatePanel(project, psiFiles, Collections.emptyList());
  }

  public static void asyncAnalyseAndUpdatePanel(
      @NotNull Project project,
      @Nullable Collection<PsiFile> psiFiles,
      @NotNull Collection<PsiFile> filesToRemove) {
    //    DumbService.getInstance(project)
    //        .runWhenSmart(
    //            () ->
    runInBackground(
        project,
        () -> {
          AnalysisData.getAnalysis(
              (psiFiles != null) ? psiFiles : DeepCodeUtils.getAllSupportedFilesInProject(project),
              filesToRemove);
          ServiceManager.getService(project, myTodoView.class).refresh();
          //      StatusBarUtil.setStatusBarInfo(project, message);
        });
  }
}
