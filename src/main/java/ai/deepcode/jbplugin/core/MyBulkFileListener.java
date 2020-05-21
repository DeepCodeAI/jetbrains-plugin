package ai.deepcode.jbplugin.core;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
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
    DCLogger.info("MyBulkFileListener.after begins");
    for (Project project : AnalysisData.getAllCachedProject()) {
      RunUtils.runInBackground(
          project,
          () -> {
            Set<PsiFile> filesChangedOrCreated =
                RunUtils.computeInReadActionInSmartMode(
                    project,
                    () ->
                        getFilteredFilesByEventTypes(
                            project,
                            events,
                            (psiFile ->
                                DeepCodeUtils.isSupportedFileFormat(psiFile)
                                    // to prevent updating files already done by
                                    // MyPsiTreeChangeAdapter
                                    // fixme: doesn't work, try to use isFromSave or isFromRefresh
                                    && AnalysisData.isHashChanged(psiFile)),
                            VFileContentChangeEvent.class,
                            // fixme doen't work for copy-past file ( VFileMoveEvent ?)
                            VFileCreateEvent.class));
            if (!filesChangedOrCreated.isEmpty()) {
              // fixme debug only
              DCLogger.info(
                  filesChangedOrCreated.size() + " files changed: " + filesChangedOrCreated);
              AnalysisData.removeFilesFromCache(filesChangedOrCreated);
              RunUtils.asyncAnalyseAndUpdatePanel(project, filesChangedOrCreated);
            }
          });

      Set<PsiFile> gcignoreChangedFiles =
          getFilteredFilesByEventTypes(
              project,
              events,
              DeepCodeIgnoreInfoHolder::is_dcignoreFile,
              VFileContentChangeEvent.class,
              VFileCreateEvent.class);
      if (!gcignoreChangedFiles.isEmpty()) {
        RunUtils.runInBackground(
            project,
            () -> {
              gcignoreChangedFiles.forEach(DeepCodeIgnoreInfoHolder::update_dcignoreFileContent);
              // small delay to prevent duplicated delete with MyPsiTreeChangeAdapter
              RunUtils.rescanProject(project, 100);
            });
      }
    }
    // fixme debug only
    DCLogger.info("MyBulkFileListener.after ends");
  }

  @Override
  public void before(@NotNull List<? extends VFileEvent> events) {
    DCLogger.info("MyBulkFileListener.before begins");
    for (Project project : AnalysisData.getAllCachedProject()) {
      if (project.isDisposed()) continue;
      Set<PsiFile> filesRemoved =
          getFilteredFilesByEventTypes(
              project, events, DeepCodeUtils::isSupportedFileFormat, VFileDeleteEvent.class);
      if (!filesRemoved.isEmpty()) {
        RunUtils.runInBackground(
            project,
            () -> {
              AnalysisData.removeFilesFromCache(filesRemoved);
              RunUtils.asyncAnalyseAndUpdatePanel(project, Collections.emptyList(), filesRemoved);
            });
      }

      Set<PsiFile> ignoreFilesToRemove =
          getFilteredFilesByEventTypes(
              project, events, DeepCodeIgnoreInfoHolder::is_ignoreFile, VFileDeleteEvent.class);
      if (!ignoreFilesToRemove.isEmpty()) {
        RunUtils.runInBackground(
            project,
            () -> {
              ignoreFilesToRemove.forEach(DeepCodeIgnoreInfoHolder::remove_dcignoreFileContent);
              // small delay to prevent duplicated delete with MyPsiTreeChangeAdapter
              RunUtils.rescanProject(project, 100);
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
        .filter(event -> PsiTreeUtil.instanceOf(event, classesOfEventsToFilter))
        .map(VFileEvent::getFile)
        .filter(Objects::nonNull)
        .map(manager::findFile)
        .filter(Objects::nonNull)
        .filter(fileFilter)
        .collect(Collectors.toSet());
  }
}
