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
      ProgressManager.checkCanceled();
    } catch (InterruptedException e) {
      e.printStackTrace();
      Thread.currentThread().interrupt();
    }
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
    if (progressManager.hasProgressIndicator()) {
      progressManager.runProcessWithProgressAsynchronously(
          myBackgroundable, progressManager.getProgressIndicator());
    } else {
      progressManager.run(myBackgroundable);
    }
  }

  private static final Map<Project, List<ProgressIndicator>> mapProject2Indicators =
      new ConcurrentHashMap<>();

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
      mapProject2Indicators.computeIfAbsent(project, p -> new ArrayList<>()).add(indicator);

      runnable.run();

      DCLogger.info("Process ending at " + project);
      mapProject2Indicators.getOrDefault(project, Collections.emptyList()).remove(indicator);
    }
  }

  // ??? list of all running background tasks
  // com.intellij.openapi.wm.ex.StatusBarEx#getBackgroundProcesses
  public static void cancelRunningIndicators(@NotNull Project project) {
    String indicatorsList =
        mapProject2Indicators.getOrDefault(project, Collections.emptyList()).stream()
            .map(ProgressIndicator::toString)
            .collect(Collectors.joining("\n"));
    DCLogger.info("Canceling ProgressIndicators:\n" + indicatorsList);
    mapProject2Indicators
        .getOrDefault(project, Collections.emptyList())
        .forEach(ProgressIndicator::cancel);
    mapProject2Indicators.remove(project);
  }

  private static final Map<VirtualFile, ProgressIndicator> mapFileProcessed2CancellableIndicator =
      new ConcurrentHashMap<>();

  public static void runInBackgroundCancellable(
      @NotNull PsiFile psiFile, @NotNull Runnable runnable) {
    DCLogger.info("runInBackgroundCancellable requested for: " + psiFile.getName());
    ProgressManager.getInstance()
        .run(
            new Task.Backgroundable(psiFile.getProject(), "DeepCode: Analysing Files...") {
              @Override
              public void run(@NotNull ProgressIndicator indicator) {
                ProgressIndicator prevProgressIndicator =
                    mapFileProcessed2CancellableIndicator.put(psiFile.getVirtualFile(), indicator);
                if (prevProgressIndicator != null
                    // can't use prevProgressIndicator.isRunning() due to
                    // https://youtrack.jetbrains.com/issue/IDEA-241055
                    && mapProject2Indicators
                        .getOrDefault(psiFile.getProject(), Collections.emptyList())
                        .contains(prevProgressIndicator)) {
                  DCLogger.info(
                      "Previous Process cancelling for "
                          + psiFile.getName()
                          + "\nProgressIndicator ["
                          + prevProgressIndicator.toString()
                          + "]");
                  prevProgressIndicator.cancel();
                }
                DCLogger.info("New Process started for " + psiFile.getName());

                runnable.run();

                DCLogger.info("Process ending for " + psiFile.getName());
              }
            });
  }

  private static final Set<String> skippingGroups = ContainerUtil.newConcurrentSet();

  public static void runInBackgroundSkipping(
      @Nullable Project project, @NotNull Runnable runnable, @NotNull String groupId) {
    DCLogger.info("runInBackgroundSkipping requested");
    if (skippingGroups.contains(groupId)) {
      DCLogger.info("Previous Process is in progress. Request skipped in group: " + groupId);
      return;
    }
    skippingGroups.add(groupId);
    ProgressManager.getInstance()
        .run(
            new Task.Backgroundable(project, "DeepCode: Analysing Files...") {
              @Override
              public void run(@NotNull ProgressIndicator indicator) {
                runnable.run();
                skippingGroups.remove(groupId);
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
