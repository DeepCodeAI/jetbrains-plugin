package ai.deepcode.jbplugin.core;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Add VFS File Listener to clear/update caches (and update Panel) for files if it was changed
 * <b>outside</b> of IDE.
 */
public class MyBulkFileListener implements BulkFileListener {

  @Override
  public void after(@NotNull List<? extends VFileEvent> events) {
    // fixme debug only
    DCLogger.getInstance()
        .logInfo("MyBulkFileListener.after begins for " + events.size() + " events " + events);
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      Set<PsiFile> filesChangedOrCreated =
          getFilteredFilesByEventTypes(
              project,
              events,
              (psiFile -> DeepCodeUtils.getInstance().isSupportedFileFormat(psiFile)),
              VFileContentChangeEvent.class,
              VFileMoveEvent.class,
              VFileCopyEvent.class,
              VFileCreateEvent.class);
      if (!filesChangedOrCreated.isEmpty()) {
        DCLogger.getInstance()
            .logInfo(filesChangedOrCreated.size() + " files changed: " + filesChangedOrCreated);
        if (filesChangedOrCreated.size() > 10) {
          // if too many files changed then it's easier to do Bulk Mode full rescan
          BulkMode.set(project);
          // small delay to prevent multiple rescan Background tasks
          RunUtils.getInstance()
              .rescanInBackgroundCancellableDelayed(project, PDU.DEFAULT_DELAY_SMALL, true);
        } else {
          for (PsiFile psiFile : filesChangedOrCreated) {
            RunUtils.getInstance()
                .runInBackgroundCancellable(
                    psiFile,
                    "Analyzing changes in " + psiFile.getName(),
                    (progress) -> {
                      AnalysisData.getInstance()
                          .removeFilesFromCache(Collections.singleton(psiFile));
                      RunUtils.getInstance()
                          .updateCachedAnalysisResults(
                              project, Collections.singleton(psiFile), progress);
                    });
          }
        }
      }

      Set<PsiFile> dcignoreChangedFiles =
          getFilteredFilesByEventTypes(
              project,
              events,
              DeepCodeIgnoreInfoHolder.getInstance()::is_dcignoreFile,
              VFileContentChangeEvent.class,
              VFileCreateEvent.class);
      if (!dcignoreChangedFiles.isEmpty()) {
        BulkMode.set(project);
        for (PsiFile dcignoreFile : dcignoreChangedFiles) {
          RunUtils.getInstance()
              .runInBackgroundCancellable(
                  dcignoreFile,
                  "Updating ignored files list...",
                  (progress) ->
                      DeepCodeIgnoreInfoHolder.getInstance()
                          .update_dcignoreFileContent(dcignoreFile));
        }
        // small delay to prevent multiple rescan Background tasks
        RunUtils.getInstance()
            .rescanInBackgroundCancellableDelayed(project, PDU.DEFAULT_DELAY_SMALL, true);
      }
    }
    // fixme debug only
    DCLogger.getInstance().logInfo("MyBulkFileListener.after ends");
  }

  @Override
  public void before(@NotNull List<? extends VFileEvent> events) {
    DCLogger.getInstance()
        .logInfo("MyBulkFileListener.before begins for " + events.size() + " events " + events);
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      // for (Project project : AnalysisData.getAllCachedProject()) {
      if (project.isDisposed()) continue;
      Set<PsiFile> filesRemoved =
          getFilteredFilesByEventTypes(
              project,
              events,
              DeepCodeUtils.getInstance()::isSupportedFileFormat,
              VFileDeleteEvent.class);
      if (!filesRemoved.isEmpty()) {
        DCLogger.getInstance()
            .logInfo("Found " + filesRemoved.size() + " files to remove: " + filesRemoved);
        // if too many files removed then it's easier to do full rescan
        if (filesRemoved.size() > 10) {
          BulkMode.set(project);
          // small delay to prevent multiple rescan Background tasks
          RunUtils.getInstance()
              .rescanInBackgroundCancellableDelayed(project, PDU.DEFAULT_DELAY_SMALL, true);
        } else if (!RunUtils.getInstance().isFullRescanRequested(project)) {
          RunUtils.getInstance()
              .runInBackground(
                  project,
                  "Removing " + filesRemoved.size() + " locally deleted files on server...",
                  (progress) -> {
                    AnalysisData.getInstance().removeFilesFromCache(PDU.toObjects(filesRemoved));
                    RunUtils.getInstance()
                        .updateCachedAnalysisResults(
                            project,
                            Collections.emptyList(),
                            PDU.toObjects(filesRemoved),
                            progress);
                  });
        }
      }

      Set<PsiFile> ignoreFilesToRemove =
          getFilteredFilesByEventTypes(
              project,
              events,
              DeepCodeIgnoreInfoHolder.getInstance()::is_ignoreFile,
              VFileDeleteEvent.class);
      if (!ignoreFilesToRemove.isEmpty()) {
        BulkMode.set(project);
        // small delay to prevent multiple rescan Background tasks
        RunUtils.getInstance()
            .rescanInBackgroundCancellableDelayed(project, PDU.DEFAULT_DELAY_SMALL, true);
      }
    }
    DCLogger.getInstance().logInfo("MyBulkFileListener.before ends");
  }

  private Predicate<VFileEvent> getUpdateModeFilter() {
    switch (DeepCodeParams.getInstance().getUpdateMode()) {
      case INTERACTIVE_MODE:
        // to prevent updating files already done by MyPsiTreeChangeAdapter
        return VFileEvent::isFromRefresh;
      case ON_SAVE_MODE:
        return VFileEvent::isFromSave;
      case MANUAL_MODE:
        return event -> false;
    }
    return event -> false;
  }

  private Set<PsiFile> getFilteredFilesByEventTypes(
      @NotNull Project project,
      @NotNull List<? extends VFileEvent> events,
      @NotNull Predicate<PsiFile> fileFilter,
      @NotNull Class<?>... classesOfEventsToFilter) {
    PsiManager manager = PsiManager.getInstance(project);
    return events.stream()
        .filter(getUpdateModeFilter())
        .filter(event -> PsiTreeUtil.instanceOf(event, classesOfEventsToFilter))
        .map(VFileEvent::getFile)
        .filter(Objects::nonNull)
        .map(manager::findFile)
        .filter(Objects::nonNull)
        .filter(fileFilter)
        .collect(Collectors.toSet());
  }
}
