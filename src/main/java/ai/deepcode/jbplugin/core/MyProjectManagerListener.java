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
    if (AnalysisData.addProjectToCache(project)) {
      // EditorFactory.getEventMulticaster.addDocumentListener BulkAwareDocumentListener
      PsiManager.getInstance(project).addPsiTreeChangeListener(new MyPsiTreeChangeAdapter());
    }
  }

  @Override
  public void projectClosing(@NotNull Project project) {
    AnalysisData.removeProjectFromCache(project);
  }

  private static class MyPsiTreeChangeAdapter extends PsiTreeChangeAdapter {
    @Override
    public void beforeChildrenChange(@NotNull PsiTreeChangeEvent event) {
      final PsiFile psiFile = event.getFile();
      if (psiFile == null) return;
      if (AnalysisData.isFileInCache(psiFile)) {
        AnalysisData.removeFilesFromCache(Collections.singleton(psiFile));
      }
    }

    @Override
    public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
      final PsiFile psiFile = event.getFile();
      if (psiFile == null) return;

      if (DeepCodeUtils.isSupportedFileFormat(psiFile)) {
        RunUtils.runInBackgroundCancellable(
            psiFile,
            () -> {
              RunUtils.asyncAnalyseAndUpdatePanel(
                  psiFile.getProject(), Collections.singleton(psiFile));
            });
      }

      if (DeepCodeIgnoreInfoHolder.is_dcignoreFile(psiFile)) {
        DeepCodeIgnoreInfoHolder.update_dcignoreFileContent(psiFile);
        // delayed to prevent unnecessary updates in case of continuous typing by user
        RunUtils.rescanProject(psiFile.getProject(), 1000);
      }
      // .gitignore content delay to be parsed https://youtrack.jetbrains.com/issue/IDEA-239773
      final VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile != null && virtualFile.getName().equals(".gitignore")) {
        final Document document = psiFile.getViewProvider().getDocument();
        if (document != null) {
          FileDocumentManager.getInstance().saveDocument(document);
          // delayed to let git update it meta-info
          RunUtils.rescanProject(psiFile.getProject(), 1000);
        }
      }
    }

    @Override
    public void beforeChildRemoval(@NotNull PsiTreeChangeEvent event) {
      PsiFile psiFile = (event.getChild() instanceof PsiFile) ? (PsiFile) event.getChild() : null;
      if (psiFile != null && DeepCodeIgnoreInfoHolder.is_ignoreFile(psiFile)) {
        DeepCodeIgnoreInfoHolder.remove_dcignoreFileContent(psiFile);
        // small delay to prevent duplicated delete with MyBulkFileListener
        RunUtils.rescanProject(psiFile.getProject(), 100);
      }
    }
  }
}
