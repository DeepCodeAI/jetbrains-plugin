package ai.deepcode.jbplugin.core;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
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
      if (psiFile!= null && AnalysisData.isFileInCache(psiFile)) {
        AnalysisData.removeFilesFromCache(Collections.singleton(psiFile));
      }
    }
  }
}
