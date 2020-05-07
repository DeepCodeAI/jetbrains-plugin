package ai.deepcode.jbplugin.core;

import ai.deepcode.jbplugin.ui.myTodoView;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.concurrency.NonUrgentExecutor;
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
      Set<PsiFile> filesChangedOrCreated =
          getFilteredFilesOfEventTypes(
              project,
              events,
              DeepCodeUtils::isSupportedFileFormat,
              VFileContentChangeEvent.class,
              VFileCreateEvent.class);
      if (!filesChangedOrCreated.isEmpty()) {
        DeepCodeUtils.runInBackground(
            project,
            () -> {
              AnalysisData.removeFilesFromCache(filesChangedOrCreated);
              DeepCodeUtils.asyncAnalyseAndUpdatePanel(project, filesChangedOrCreated);
            });
      }

      Set<PsiFile> gcignoreChangedFiles =
          getFilteredFilesOfEventTypes(
              project,
              events,
              DeepCodeIgnoreInfoHolder::is_dcignoreFile,
              VFileContentChangeEvent.class,
              VFileCreateEvent.class);
      if (!gcignoreChangedFiles.isEmpty()) {
        DeepCodeUtils.runInBackground(
            project,
            () -> {
              gcignoreChangedFiles.forEach(DeepCodeIgnoreInfoHolder::update_dcignoreFileContent);
              AnalysisData.clearCache(project);
              DeepCodeUtils.asyncAnalyseProjectAndUpdatePanel(project);
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
      Set<PsiFile> filesRemoved =
          getFilteredFilesOfEventTypes(
              project, events, DeepCodeUtils::isSupportedFileFormat, VFileDeleteEvent.class);
      if (!filesRemoved.isEmpty()) {
        DeepCodeUtils.runInBackground(
            project,
            () -> {
              AnalysisData.removeFilesFromCache(filesRemoved);
              // ReadAction.nonBlocking(
              AnalysisData.retrieveSuggestions(Collections.emptyList(), filesRemoved);
              ServiceManager.getService(project, myTodoView.class).refresh();
            });
      }

      Set<PsiFile> gcignoreChangedFiles =
          getFilteredFilesOfEventTypes(
              project, events, DeepCodeIgnoreInfoHolder::is_dcignoreFile, VFileDeleteEvent.class);
      if (!gcignoreChangedFiles.isEmpty()) {
        // ReadAction needed to avoid deadlock with other ReadActions (parseGetAnalysisResponse)
        DeepCodeUtils.runInBackground(
            project,
            () -> {
              gcignoreChangedFiles.forEach(DeepCodeIgnoreInfoHolder::remove_dcignoreFileContent);
              AnalysisData.clearCache(project);
              DeepCodeUtils.asyncAnalyseProjectAndUpdatePanel(project);
            });
      }
    }
    DCLogger.info("MyBulkFileListener.before ends");
  }

  private Set<PsiFile> getFilteredFilesOfEventTypes(
      @NotNull Project project,
      @NotNull List<? extends VFileEvent> events,
      @NotNull Predicate<PsiFile> fileFilter,
      @NotNull Class<?>... classesOfEventsToFilter) {
    PsiManager manager = PsiManager.getInstance(project);
    return events.stream()
        //        .filter(MyBulkFileListener::isEventFromOutside)
        .filter(event -> PsiTreeUtil.instanceOf(event, classesOfEventsToFilter))
        .map(VFileEvent::getFile)
        .filter(Objects::nonNull)
        .map(manager::findFile)
        .filter(Objects::nonNull)
        .filter(fileFilter)
        .collect(Collectors.toSet());
  }

  private static boolean isEventFromOutside(VFileEvent event) {
    return true;
  }
}
