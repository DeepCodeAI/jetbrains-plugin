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
    DCLogger.info("MyBulkFileListener.after begins for " + events.size() + " events " + events);
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
              (psiFile -> DeepCodeUtils.isSupportedFileFormat(psiFile)
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
        DCLogger.info(filesChangedOrCreated.size() + " files changed: " + filesChangedOrCreated);
        if (filesChangedOrCreated.size() > 10) {
          // if too many files changed then it's easier to do Bulk Mode full rescan
          RunUtils.setBulkMode(project);
          RunUtils.runInBackground(
              project,
              () -> {
                // small delay to prevent multiple rescan
                RunUtils.rescanProject(project, 100);
/*
                AnalysisData.removeFilesFromCache(filesChangedOrCreated);
                RunUtils.updateCachedAnalysisResults(project, filesChangedOrCreated);
*/
                RunUtils.unsetBulkMode(project);
              });
        } else {
          for (PsiFile psiFile : filesChangedOrCreated) {
            RunUtils.runInBackgroundCancellable(
                psiFile,
                () -> {
                  AnalysisData.removeFilesFromCache(Collections.singleton(psiFile));
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
              DeepCodeIgnoreInfoHolder::is_dcignoreFile,
              VFileContentChangeEvent.class,
              VFileCreateEvent.class);
      if (!gcignoreChangedFiles.isEmpty()) {
        RunUtils.setBulkMode(project);
        RunUtils.runInBackground(
            project,
            () -> {
              gcignoreChangedFiles.forEach(DeepCodeIgnoreInfoHolder::update_dcignoreFileContent);
              // small delay to prevent multiple rescan
              RunUtils.rescanProject(project, 100);
              RunUtils.unsetBulkMode(project);
            });
      }
    }
    // fixme debug only
    DCLogger.info("MyBulkFileListener.after ends");
  }

  @Override
  public void before(@NotNull List<? extends VFileEvent> events) {
    DCLogger.info("MyBulkFileListener.before begins for " + events.size() + " events " + events);
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      // for (Project project : AnalysisData.getAllCachedProject()) {
      if (project.isDisposed()) continue;
      Set<PsiFile> filesRemoved =
          getFilteredFilesByEventTypes(
              project, events, DeepCodeUtils::isSupportedFileFormat, VFileDeleteEvent.class);
      if (!filesRemoved.isEmpty()) {
        DCLogger.info("Found " + filesRemoved.size() + " files to remove: " + filesRemoved);
        RunUtils.setBulkMode(project);
        if (filesRemoved.size() > 10) {
          // if too many files removed then it's easier to do full rescan
          RunUtils.runInBackground(
              project,
              () -> {
                // small delay to prevent multiple rescan
                RunUtils.rescanProject(project, 100);
                RunUtils.unsetBulkMode(project);
              });
        } else {
          RunUtils.runInBackground(
              project,
              () -> {
                AnalysisData.removeFilesFromCache(filesRemoved);
                RunUtils.updateCachedAnalysisResults(
                    project, Collections.emptyList(), filesRemoved);
                RunUtils.unsetBulkMode(project);
              });
        }
      }

      Set<PsiFile> ignoreFilesToRemove =
          getFilteredFilesByEventTypes(
              project, events, DeepCodeIgnoreInfoHolder::is_ignoreFile, VFileDeleteEvent.class);
      if (!ignoreFilesToRemove.isEmpty()) {
        RunUtils.setBulkMode(project);
        RunUtils.runInBackground(
            project,
            () -> {
              ignoreFilesToRemove.forEach(DeepCodeIgnoreInfoHolder::remove_dcignoreFileContent);
              // small delay to prevent multiple rescan
              RunUtils.rescanProject(project, 100);
              RunUtils.unsetBulkMode(project);
            });
      }
    }
    DCLogger.info("MyBulkFileListener.before ends");
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
