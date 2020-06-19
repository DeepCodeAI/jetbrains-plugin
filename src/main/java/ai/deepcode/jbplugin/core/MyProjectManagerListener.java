package ai.deepcode.jbplugin.core;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

/** Add PsiTree change Listener to clear caches for file if it was changed. */
public class MyProjectManagerListener implements ProjectManagerListener {

  public MyProjectManagerListener(@NotNull Project project) {
    projectOpened(project);
  }

  @Override
  public void projectOpened(@NotNull Project project) {
    if (AnalysisData.getInstance().addProjectToCache(project)) {
      // EditorFactory.getEventMulticaster.addDocumentListener BulkAwareDocumentListener
      PsiManager.getInstance(project).addPsiTreeChangeListener(new MyPsiTreeChangeAdapter());
    }
  }

  @Override
  public void projectClosing(@NotNull Project project) {
    RunUtils.runInBackground(project, () -> {
      // lets all running ProgressIndicators release MUTEX first
      RunUtils.cancelRunningIndicators(project);
      AnalysisData.getInstance().removeProjectFromCaches(project);
    });
  }

  private static class MyPsiTreeChangeAdapter extends PsiTreeChangeAdapter {
    @Override
    public void beforeChildrenChange(@NotNull PsiTreeChangeEvent event) {
      final PsiFile psiFile = event.getFile();
      if (psiFile == null || BulkMode.isActive(psiFile.getProject())) return;
      if (AnalysisData.getInstance().isFileInCache(psiFile)) {
        // ?? immediate delete for visual updates in Panel, annotations, etc.
        // should be done in background to wait MUTEX released in case of currently running update
        RunUtils.runInBackground(
            psiFile.getProject(),
            () -> AnalysisData.getInstance().removeFilesFromCache(Collections.singleton(psiFile)));
      }
      /*
            if (DeepCodeUtils.isSupportedFileFormat(psiFile)) {
              // should be done in background to wait MUTEX released in case of currently running update
              RunUtils.runInBackground(
                  psiFile.getProject(),
                  () -> {
                    // to prevent updating already done by MyBulkFileListener
                    if (AnalysisData.isHashChanged(psiFile)) {
                      AnalysisData.removeFilesFromCache(Collections.singleton(psiFile));
                    }
                  });
            }
      */
    }

    @Override
    public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
      final PsiFile psiFile = event.getFile();
      if (psiFile == null) return;
      final Project project = psiFile.getProject();
      if (BulkMode.isActive(project)) return;

      if (DeepCodeUtils.getInstance().isSupportedFileFormat(psiFile)) {
        RunUtils.runInBackgroundCancellable(
            psiFile,
            () -> {
              if (AnalysisData.getInstance().isFileInCache(psiFile)) {
                // should be already deleted at beforeChildrenChange in most cases,
                // but in case of update finished between beforeChildrenChange and now.
                AnalysisData.getInstance().removeFilesFromCache(Collections.singleton(psiFile));
              }
              RunUtils.updateCachedAnalysisResults(project, Collections.singleton(psiFile));
            });
      }

      if (DeepCodeIgnoreInfoHolder.getInstance().is_dcignoreFile(psiFile)) {
        DeepCodeIgnoreInfoHolder.getInstance().update_dcignoreFileContent(psiFile);
        // delayed to prevent unnecessary updates in case of continuous typing by user
        RunUtils.rescanInBackgroundCancellableDelayed(project, PDU.DEFAULT_DELAY, false);
      }
      // .gitignore content delay to be parsed https://youtrack.jetbrains.com/issue/IDEA-239773
      final VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile != null && virtualFile.getName().equals(".gitignore")) {
        final Document document = psiFile.getViewProvider().getDocument();
        if (document != null) {
          FileDocumentManager.getInstance().saveDocument(document);
          // delayed to let git update it meta-info
          RunUtils.rescanInBackgroundCancellableDelayed(project, PDU.DEFAULT_DELAY, false);
        }
      }
    }

    @Override
    public void beforeChildRemoval(@NotNull PsiTreeChangeEvent event) {
      PsiFile psiFile = (event.getChild() instanceof PsiFile) ? (PsiFile) event.getChild() : null;
      if (psiFile == null) return;
      final Project project = psiFile.getProject();
      if (BulkMode.isActive(project)) return;

      if (DeepCodeIgnoreInfoHolder.getInstance().is_ignoreFile(psiFile)) {
        DeepCodeIgnoreInfoHolder.getInstance().remove_dcignoreFileContent(psiFile);
        // ??? small delay to prevent duplicated delete with MyBulkFileListener
        RunUtils.rescanInBackgroundCancellableDelayed(project, PDU.DEFAULT_DELAY_SMALL, false);
      }
    }
  }
}
