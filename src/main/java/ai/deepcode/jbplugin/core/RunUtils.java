package ai.deepcode.jbplugin.core;

import ai.deepcode.javaclient.core.RunUtilsBase;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Computable;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.function.Consumer;

public class RunUtils extends RunUtilsBase {

  private static final RunUtils INSTANCE = new RunUtils();

  public static RunUtils getInstance() {
    return INSTANCE;
  }

  private RunUtils() {
    super(
        PDU.getInstance(),
        HashContentUtils.getInstance(),
        AnalysisData.getInstance(),
        DeepCodeUtils.getInstance(),
        DCLogger.getInstance());
  }

  public static <T> T computeInReadActionInSmartMode(
      @NotNull Project project, @NotNull final Computable<T> computation) {
    // DCLogger.getInstance().info("computeInReadActionInSmartMode requested");
    final DumbService dumbService =
        ReadAction.compute(() -> project.isDisposed() ? null : DumbService.getInstance(project));
    if (dumbService == null) {
      DCLogger.getInstance().logWarn("dumbService == null");
      return null;
    }
    return dumbService.runReadActionInSmartMode(
            () -> {
              // DCLogger.getInstance().info("computeInReadActionInSmartMode actually executing");
              return computation.compute();
            });
  }

  /**
   * Should implement reuse of currently running parent DeepCode Progress if possible
   *
   * @return true if reuse been successful
   */
  @Override
  protected boolean reuseCurrentProgress(
      @NotNull Object project, @NotNull String title, @NotNull Consumer<Object> progressConsumer) {
    final ProgressManager progressManager = ProgressManager.getInstance();
    final ProgressIndicator progressIndicator = progressManager.getProgressIndicator();
    if (getRunningProgresses(PDU.toProject(project)).contains(progressIndicator)) {
      final MyBackgroundable myBackgroundable =
          new MyBackgroundable(PDU.toProject(project), title, progressConsumer);
      progressManager.runProcessWithProgressAsynchronously(myBackgroundable, progressIndicator);
      return true;
    }
    return false;
  }

  /** Should implement background task creation with call of progressConsumer() inside Job.run() */
  @Override
  protected void doBackgroundRun(
      @NotNull Object project, @NotNull String title, @NotNull Consumer<Object> progressConsumer) {
    ProgressManager.getInstance()
        .run(new MyBackgroundable(PDU.toProject(project), title, progressConsumer));
  }

  @NotNull
  private static ProgressIndicator toProgress(@NotNull Object progress) {
    if (!(progress instanceof ProgressIndicator))
      throw new IllegalArgumentException("progress should be ProgressIndicator instance");
    return (ProgressIndicator) progress;
  }

  @Override
  protected void cancelProgress(@NotNull Object progress) {
    toProgress(progress).cancel();
  }

  @Override
  protected void bulkModeForceUnset(@NotNull Object project) {
    // in case any indicator holds Bulk mode process
    BulkMode.forceUnset(PDU.toProject(project));
  }

  @Override
  protected void bulkModeUnset(@NotNull Object project) {
    BulkMode.unset(PDU.toProject(project));
  }

  @Override
  protected void updateAnalysisResultsUIPresentation(@NotNull Collection<Object> files) {
    // code from T0D0 already have listener for updates
  }

  private class MyBackgroundable extends Task.Backgroundable {
    private @NotNull final Project project;
    private @NotNull final Consumer<Object> consumer;

    public MyBackgroundable(
        @NotNull Project project, @NotNull String title, @NotNull Consumer<Object> consumer) {
      super(project, "DeepCode: " + title);
      this.project = project;
      this.consumer = consumer;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      indicator.setIndeterminate(false);
      consumer.accept(indicator);
    }
  }
}
