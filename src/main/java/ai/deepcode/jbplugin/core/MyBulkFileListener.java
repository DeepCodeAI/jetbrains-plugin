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
    DCLogger.getInstance().logInfo("MyBulkFileListener.after begins for " + events.size() + " events " + events);
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      /*
          for (Project project : AnalysisData.getAllCachedProject()) {
            RunUtils.runInBackground(
                project,
                () -> {
      */
      Set<PsiFile> filesChangedOrCreated =
          /*
                          RunUtils.computeInReadActionInSmartMode(
                              project,
                              () ->
          */
          getFilteredFilesByEventTypes(
              project,
              events,
              (psiFile -> DeepCodeUtils.getInstance().isSupportedFileFormat(psiFile)
              // to prevent updating files already done by
              // MyPsiTreeChangeAdapter
              // fixme: doesn't work, try to use isFromSave or isFromRefresh
              // && AnalysisData.isHashChanged(psiFile)
              ),
              VFileContentChangeEvent.class,
              VFileMoveEvent.class,
              VFileCopyEvent.class,
              VFileCreateEvent.class);
      if (!filesChangedOrCreated.isEmpty()) {
        DCLogger.getInstance().logInfo(filesChangedOrCreated.size() + " files changed: " + filesChangedOrCreated);
        if (filesChangedOrCreated.size() > 10) {
          // if too many files changed then it's easier to do Bulk Mode full rescan
          BulkMode.set(project);
          // small delay to prevent multiple rescan Background tasks
          RunUtils.rescanInBackgroundCancellableDelayed(project, PDU.DEFAULT_DELAY_SMALL, true);
        } else {
          for (PsiFile psiFile : filesChangedOrCreated) {
            RunUtils.runInBackgroundCancellable(
                psiFile,
                () -> {
                  AnalysisData.getInstance().removeFilesFromCache(Collections.singleton(psiFile));
                  RunUtils.updateCachedAnalysisResults(project, Collections.singleton(psiFile));
                });
          }
        }
      }
      //          });

      Set<PsiFile> gcignoreChangedFiles =
          getFilteredFilesByEventTypes(
              project,
              events,
              DeepCodeIgnoreInfoHolder.getInstance()::is_dcignoreFile,
              VFileContentChangeEvent.class,
              VFileCreateEvent.class);
      if (!gcignoreChangedFiles.isEmpty()) {
        BulkMode.set(project);
        for (PsiFile gcignoreFile : gcignoreChangedFiles) {
          RunUtils.runInBackgroundCancellable(
              gcignoreFile,
              () -> DeepCodeIgnoreInfoHolder.getInstance().update_dcignoreFileContent(gcignoreFile));
        }
        // small delay to prevent multiple rescan Background tasks
        RunUtils.rescanInBackgroundCancellableDelayed(project, PDU.DEFAULT_DELAY_SMALL, true);
      }
    }
    // fixme debug only
    DCLogger.getInstance().logInfo("MyBulkFileListener.after ends");
  }

  @Override
  public void before(@NotNull List<? extends VFileEvent> events) {
    DCLogger.getInstance().logInfo("MyBulkFileListener.before begins for " + events.size() + " events " + events);
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      // for (Project project : AnalysisData.getAllCachedProject()) {
      if (project.isDisposed()) continue;
      Set<PsiFile> filesRemoved =
          getFilteredFilesByEventTypes(
              project, events, DeepCodeUtils.getInstance()::isSupportedFileFormat, VFileDeleteEvent.class);
      if (!filesRemoved.isEmpty()) {
        DCLogger.getInstance().logInfo("Found " + filesRemoved.size() + " files to remove: " + filesRemoved);
        // if too many files removed then it's easier to do full rescan
        if (filesRemoved.size() > 10) {
          BulkMode.set(project);
          // small delay to prevent multiple rescan Background tasks
          RunUtils.rescanInBackgroundCancellableDelayed(project, PDU.DEFAULT_DELAY_SMALL, true);
        } else if (!RunUtils.isFullRescanRequested(project)) {
          RunUtils.runInBackground(
              project,
              () -> {
                AnalysisData.getInstance().removeFilesFromCache(PDU.toObjects(filesRemoved));
                RunUtils.updateCachedAnalysisResults(
                    project, Collections.emptyList(), filesRemoved);
              });
        }
      }

      Set<PsiFile> ignoreFilesToRemove =
          getFilteredFilesByEventTypes(
              project, events, DeepCodeIgnoreInfoHolder.getInstance()::is_ignoreFile, VFileDeleteEvent.class);
      if (!ignoreFilesToRemove.isEmpty()) {
        BulkMode.set(project);
        // small delay to prevent multiple rescan Background tasks
        RunUtils.rescanInBackgroundCancellableDelayed(project, PDU.DEFAULT_DELAY_SMALL, true);
        /*
                RunUtils.rescanInBackgroundCancellableDelayed(
                    project,
                    100, // small delay to prevent multiple rescan
                    () -> {
                      ignoreFilesToRemove.forEach(DeepCodeIgnoreInfoHolder::remove_dcignoreFileContent);
                      RunUtils.rescanProject(project);
                      RunUtils.unsetBulkMode(project);
                    });
        */
      }
    }
    DCLogger.getInstance().logInfo("MyBulkFileListener.before ends");
  }

  private Set<PsiFile> getFilteredFilesByEventTypes(
      @NotNull Project project,
      @NotNull List<? extends VFileEvent> events,
      @NotNull Predicate<PsiFile> fileFilter,
      @NotNull Class<?>... classesOfEventsToFilter) {
    PsiManager manager = PsiManager.getInstance(project);
    return events.stream()
        // to prevent updating files already done by MyPsiTreeChangeAdapter
        .filter(VFileEvent::isFromRefresh)
        .filter(event -> PsiTreeUtil.instanceOf(event, classesOfEventsToFilter))
        .map(VFileEvent::getFile)
        .filter(Objects::nonNull)
        .map(manager::findFile)
        .filter(Objects::nonNull)
        .filter(fileFilter)
        .collect(Collectors.toSet());
  }
}
