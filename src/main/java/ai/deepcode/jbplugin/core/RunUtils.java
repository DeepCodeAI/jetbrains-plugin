package ai.deepcode.jbplugin.core;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
    // DCLogger.info("computeInReadActionInSmartMode requested");
    T result = null;
    final DumbService dumbService =
        ReadAction.compute(() -> project.isDisposed() ? null : DumbService.getInstance(project));
    if (dumbService == null) return result;
    result =
        dumbService.runReadActionInSmartMode(
            () -> {
              // DCLogger.info("computeInReadActionInSmartMode actually executing");
              return computation.compute();
            });
    return result;
  }

  public static void delay(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      DCLogger.warn("InterruptedException: " + e.getMessage());
      Thread.currentThread().interrupt();
    }
    ProgressManager.checkCanceled();
  }

  private static long timeOfLastRescanRequest = 0;
  // BackgroundTaskQueue ??? com.intellij.openapi.wm.ex.StatusBarEx#getBackgroundProcesses ???
  public static void rescanProject(@NotNull Project project, long delayMilliseconds) {
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

  public static void runInBackground(@NotNull Project project, @NotNull Runnable runnable) {
    DCLogger.info("runInBackground requested");
    final ProgressManager progressManager = ProgressManager.getInstance();
    final MyBackgroundable myBackgroundable = new MyBackgroundable(project, runnable);
    final ProgressIndicator progressIndicator = progressManager.getProgressIndicator();
    if (getRunningIndicators(project).contains(progressIndicator)) {
      progressManager.runProcessWithProgressAsynchronously(myBackgroundable, progressIndicator);
    } else {
      progressManager.run(myBackgroundable);
    }
  }

  private static class MyBackgroundable extends Task.Backgroundable {
    private @NotNull final Project project;
    private @NotNull final Runnable runnable;

    public MyBackgroundable(@NotNull Project project, @NotNull Runnable runnable) {
      super(project, "DeepCode: Analysing Files...");
      this.project = project;
      this.runnable = runnable;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      DCLogger.info("New Process started at " + project);
      getRunningIndicators(project).add(indicator);

      runnable.run();

      DCLogger.info("Process ending at " + project);
      getRunningIndicators(project).remove(indicator);
    }
  }

  private static final Map<Project, Set<ProgressIndicator>> mapProject2Indicators =
      new ConcurrentHashMap<>();

  private static Set<ProgressIndicator> getRunningIndicators(@NotNull Project project) {
    return mapProject2Indicators.computeIfAbsent(project, p -> new HashSet<>());
  }

  // ??? list of all running background tasks
  // com.intellij.openapi.wm.ex.StatusBarEx#getBackgroundProcesses
  // todo? Disposer.register(project, this)
  // https://intellij-support.jetbrains.com/hc/en-us/community/posts/360008241759/comments/360001689399
  public static void cancelRunningIndicators(@NotNull Project project) {
    String indicatorsList =
        getRunningIndicators(project).stream()
            .map(ProgressIndicator::toString)
            .collect(Collectors.joining("\n"));
    DCLogger.info("Canceling ProgressIndicators:\n" + indicatorsList);
    getRunningIndicators(project).forEach(ProgressIndicator::cancel);
    getRunningIndicators(project).clear();
  }

  private static final Map<VirtualFile, ProgressIndicator> mapFileProcessed2CancellableIndicator =
      new ConcurrentHashMap<>();

  public static void runInBackgroundCancellable(
      @NotNull PsiFile psiFile, @NotNull Runnable runnable) {
    DCLogger.info("runInBackgroundCancellable requested for: " + psiFile.getName());
    final Project project = psiFile.getProject();
    ProgressManager.getInstance()
        .run(
            new Task.Backgroundable(project, "DeepCode: Analysing Files...") {
              @Override
              public void run(@NotNull ProgressIndicator indicator) {
                ProgressIndicator prevProgressIndicator =
                    mapFileProcessed2CancellableIndicator.put(psiFile.getVirtualFile(), indicator);
                if (prevProgressIndicator != null
                    // can't use prevProgressIndicator.isRunning() due to
                    // https://youtrack.jetbrains.com/issue/IDEA-241055
                    && getRunningIndicators(project).contains(prevProgressIndicator)) {
                  DCLogger.info(
                      "Previous Process cancelling for "
                          + psiFile.getName()
                          + "\nProgressIndicator ["
                          + prevProgressIndicator.toString()
                          + "]");
                  prevProgressIndicator.cancel();
                  getRunningIndicators(project).remove(prevProgressIndicator);
                }
                getRunningIndicators(project).add(indicator);

                // small delay to let new consequent requests proceed and cancel current one
                delay(100);

                DCLogger.info("New Process started for " + psiFile.getName());
                runnable.run();
                DCLogger.info("Process ending for " + psiFile.getName());
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
          AnalysisData.updateCachedResultsForFiles(
              project,
              (psiFiles != null) ? psiFiles : DeepCodeUtils.getAllSupportedFilesInProject(project),
              filesToRemove);
          //      StatusBarUtil.setStatusBarInfo(project, message);
        });
  }
}
