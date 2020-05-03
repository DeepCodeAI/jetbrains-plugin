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
import java.util.stream.Collectors;

/**
 * Add VFS File Listener to clear/update caches (and update Panel) for files if it was changed
 * outside of IDE.
 */
public class MyBulkFileListener implements BulkFileListener {
  @Override
  public void after(@NotNull List<? extends VFileEvent> events) {
    for (Project project : AnalysisData.getAllCachedProject()) {
      Set<PsiFile> filesChangedOrCreated =
          getFilesOfEventTypes(
              project, events, VFileContentChangeEvent.class, VFileCreateEvent.class);
      if (!filesChangedOrCreated.isEmpty()) {
        AnalysisData.removeFilesFromCache(filesChangedOrCreated);
        DeepCodeUtils.asyncAnalyseAndUpdatePanel(project, filesChangedOrCreated);
      }
    }
  }

  @Override
  public void before(@NotNull List<? extends VFileEvent> events) {
    for (Project project : AnalysisData.getAllCachedProject()) {
      Set<PsiFile> filesRemoved = getFilesOfEventTypes(project, events, VFileDeleteEvent.class);
      if (!filesRemoved.isEmpty()) {
        AnalysisData.removeFilesFromCache(filesRemoved);
        ReadAction.nonBlocking(
                () -> {
                  AnalysisData.retrieveSuggestions(Collections.emptyList(), filesRemoved);
                })
            .submit(NonUrgentExecutor.getInstance());
        ServiceManager.getService(project, myTodoView.class).refresh();
      }
    }
  }

  private Set<PsiFile> getFilesOfEventTypes(
      @NotNull Project project,
      @NotNull List<? extends VFileEvent> events,
      @NotNull Class<?>... classesOfEventsToFilter) {
    PsiManager manager = PsiManager.getInstance(project);
    return events.stream()
        .filter(event -> PsiTreeUtil.instanceOf(event, classesOfEventsToFilter))
        .map(VFileEvent::getFile)
        .filter(Objects::nonNull)
        .map(manager::findFile)
        .filter(DeepCodeUtils::isSupportedFileFormat)
        .collect(Collectors.toSet());
  }
}
