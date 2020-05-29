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

  private static final Map<Project, Integer> mapProject2RequestsCounter = new ConcurrentHashMap<>();

  private static int getBulkRequestsCount(@NotNull Project project) {
    return mapProject2RequestsCounter.computeIfAbsent(project, p -> 0);
  }

  /** No events for individual files should be processed */
  public static boolean inBulkMode(@NotNull Project project) {
    return getBulkRequestsCount(project) > 0;
  }

  public static void setBulkMode(@NotNull Project project) {
    final int counter = getBulkRequestsCount(project) + 1;
    if (counter == 1) {
      // cancel all running tasks first
      cancelRunningIndicators(project);
    }
    DCLogger.info("BulkMode ON with " + counter + " total requests");
    mapProject2RequestsCounter.put(project, counter);
  }

  public static void unsetBulkMode(@NotNull Project project) {
    final int counter = getBulkRequestsCount(project) - 1;
    if (counter >= 0) {
      mapProject2RequestsCounter.put(project, counter);
      DCLogger.info("BulkMode OFF with " + counter + " total requests");
    } else {
      DCLogger.warn("BulkMode OFF request with already " + counter + " total requests");
    }
  }

  public static void forceUnsetBulkMode(@NotNull Project project) {
    mapProject2RequestsCounter.put(project, 0);
    DCLogger.info("BulkMode OFF forced");
  }

  public static void asyncUpdateCurrentFilePanel(PsiFile psiFile) {}

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

  private static synchronized Set<ProgressIndicator> getRunningIndicators(
      @NotNull Project project) {
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
    // in case any indicator holds Bulk mode process
    forceUnsetBulkMode(project);
    getRunningIndicators(project).forEach(ProgressIndicator::cancel);
    getRunningIndicators(project).clear();
    projectsWithFullRescanRequested.remove(project);
  }

  private static final Map<VirtualFile, ProgressIndicator> mapFileProcessed2CancellableIndicator =
      new ConcurrentHashMap<>();

  private static final Map<VirtualFile, Runnable> mapFile2Runnable = new ConcurrentHashMap<>();

  public static void runInBackgroundCancellable(
      @NotNull PsiFile psiFile, @NotNull Runnable runnable) {
    final String s = runnable.toString();
    final String runId = s.substring(s.lastIndexOf('/'), s.length() - 1);
    DCLogger.info(
        "runInBackgroundCancellable requested for: "
            + psiFile.getName()
            + " with Runnable "
            + runId);
    final VirtualFile virtualFile = psiFile.getVirtualFile();

    // To proceed multiple PSI events in a bunch (every 100 milliseconds)
    Runnable prevRunnable = mapFile2Runnable.put(virtualFile, runnable);
    if (prevRunnable != null) return;
    DCLogger.info(
        "new Background task registered for: " + psiFile.getName() + " with Runnable " + runId);

    final Project project = psiFile.getProject();
    ProgressManager.getInstance()
        .run(
            new Task.Backgroundable(project, "DeepCode: Analysing Files...") {
              @Override
              public void run(@NotNull ProgressIndicator indicator) {

                // To let new event cancel the currently running one
                ProgressIndicator prevProgressIndicator =
                    mapFileProcessed2CancellableIndicator.put(virtualFile, indicator);
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

                Runnable actualRunnable = mapFile2Runnable.get(virtualFile);
                if (actualRunnable != null) {
                  final String s1 = actualRunnable.toString();
                  final String runId = s1.substring(s1.lastIndexOf('/'), s1.length() - 1);
                  DCLogger.info(
                      "New Process started for " + psiFile.getName() + " with Runnable " + runId);
                  mapFile2Runnable.remove(virtualFile);
                  actualRunnable.run();
                } else {
                  DCLogger.warn("No actual Runnable found for: " + psiFile.getName());
                }
                DCLogger.info("Process ending for " + psiFile.getName());
              }
            });
  }

  public static boolean isFullRescanRequested(@NotNull Project project) {
    return projectsWithFullRescanRequested.contains(project);
  }

  private static final Set<Project> projectsWithFullRescanRequested =
      ContainerUtil.newConcurrentSet();

  private static final Map<Project, ProgressIndicator> mapProject2CancellableIndicator =
      new ConcurrentHashMap<>();
  private static final Map<Project, Long> mapProject2CancellableRequestId =
      new ConcurrentHashMap<>();

  private static final Map<Project, Long> mapProject2RequestId = new ConcurrentHashMap<>();
  private static final Set<Long> bulkModeRequests = ContainerUtil.newConcurrentSet();

  public static void rescanInBackgroundCancellableDelayed(
      @NotNull Project project, long delayMilliseconds, boolean inBulkMode) {
    final long requestId = System.currentTimeMillis();
    DCLogger.info(
        "rescanInBackgroundCancellableDelayed requested for: "
            + project.getName()
            + "] with RequestId "
            + requestId);
    projectsWithFullRescanRequested.add(project);

    // To proceed multiple events in a bunch (every <delayMilliseconds>)
    Long prevRequestId = mapProject2RequestId.put(project, requestId);
    if (inBulkMode) bulkModeRequests.add(requestId);
    if (prevRequestId != null) {
      if (bulkModeRequests.remove(prevRequestId)) {
        RunUtils.unsetBulkMode(project);
      }
      return;
    }
    DCLogger.info(
        "new Background Rescan task registered for ["
            + project.getName()
            + "] with RequestId "
            + requestId);

    ProgressManager.getInstance()
        .run(
            new Task.Backgroundable(project, "DeepCode: Analysing Files...") {
              @Override
              public void run(@NotNull ProgressIndicator indicator) {

                // To let new event cancel the currently running one
                ProgressIndicator prevProgressIndicator =
                    mapProject2CancellableIndicator.put(project, indicator);
                if (prevProgressIndicator != null
                    // can't use prevProgressIndicator.isRunning() due to
                    // https://youtrack.jetbrains.com/issue/IDEA-241055
                    && getRunningIndicators(project).remove(prevProgressIndicator)) {
                  DCLogger.info(
                      "Previous Rescan cancelling for "
                          + project.getName()
                          + "\nProgressIndicator ["
                          + prevProgressIndicator.toString()
                          + "]");
                  prevProgressIndicator.cancel();
                }
                getRunningIndicators(project).add(indicator);

                // unset BulkMode if cancelled process did run under BulkMode
                Long prevRequestId = mapProject2CancellableRequestId.put(project, requestId);
                if (prevRequestId != null && bulkModeRequests.remove(prevRequestId)) {
                  RunUtils.unsetBulkMode(project);
                }

                // delay to let new consequent requests proceed and cancel current one
                // or to let Idea proceed internal events (.gitignore update)
                delay(delayMilliseconds);

                Long actualRequestId = mapProject2RequestId.get(project);
                if (actualRequestId != null) {
                  DCLogger.info(
                      "New Rescan started for ["
                          + project.getName()
                          + "] with RequestId "
                          + actualRequestId);
                  mapProject2RequestId.remove(project);

                  // actual rescan
                  AnalysisData.removeProjectFromCaches(project);
                  updateCachedAnalysisResults(project, null);

                  if (bulkModeRequests.remove(actualRequestId)) {
                    RunUtils.unsetBulkMode(project);
                  }
                } else {
                  DCLogger.warn("No actual RequestId found for: " + project.getName());
                }
                projectsWithFullRescanRequested.remove(project);
                DCLogger.info("Rescan ending for " + project.getName());
              }
            });
  }

  public static void asyncAnalyseProjectAndUpdatePanel(@Nullable Project project) {
    final Project[] projects =
        (project == null)
            ? ProjectManager.getInstance().getOpenProjects()
            : new Project[] {project};
    for (Project prj : projects) {
      //    DumbService.getInstance(project).runWhenSmart(() ->
      runInBackground(prj, () -> updateCachedAnalysisResults(prj, null));
    }
  }

  public static void updateCachedAnalysisResults(
      @NotNull Project project, @Nullable Collection<PsiFile> psiFiles) {
    updateCachedAnalysisResults(project, psiFiles, Collections.emptyList());
  }

  public static void updateCachedAnalysisResults(
      @NotNull Project project,
      @Nullable Collection<PsiFile> psiFiles,
      @NotNull Collection<PsiFile> filesToRemove) {
    AnalysisData.updateCachedResultsForFiles(
        project,
        (psiFiles != null) ? psiFiles : DeepCodeUtils.getAllSupportedFilesInProject(project),
        filesToRemove);
    //      StatusBarUtil.setStatusBarInfo(project, message);
  }
}
