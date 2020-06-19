package ai.deepcode.jbplugin.core;

import ai.deepcode.javaclient.core.DeepCodeUtilsBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class DeepCodeUtils extends DeepCodeUtilsBase {

  private static final DeepCodeUtils INSTANCE = new DeepCodeUtils();
  private final DCLogger dcLogger = DCLogger.getInstance();

  private DeepCodeUtils() {
    super(
        AnalysisData.getInstance(),
        DeepCodeParams.getInstance(),
        DeepCodeIgnoreInfoHolder.getInstance(),
        DCLogger.getInstance());
  }

  public static DeepCodeUtilsBase getInstance() {
    return INSTANCE;
  }

  @Override
  protected Collection<Object> allProjectFiles(@NotNull Object projectO) {
    Project project = PDU.toProject(projectO);
    return RunUtils.computeInReadActionInSmartMode(
        project,
        () -> {
          final PsiManager psiManager = PsiManager.getInstance(project);
          final VirtualFile projectDir = ProjectUtil.guessProjectDir(project);
          if (projectDir == null) {
            dcLogger.logWarn("Project directory not found for: " + project);
            return Collections.emptyList();
          }
          final PsiDirectory prjDirectory = psiManager.findDirectory(projectDir);
          if (prjDirectory == null) {
            dcLogger.logWarn("Project directory not found for: " + project);
            return Collections.emptyList();
          }
          return PDU.toObjects(getFilesRecursively(prjDirectory));
        });
  }

  private List<PsiFile> getFilesRecursively(@NotNull PsiDirectory psiDirectory) {
    List<PsiFile> psiFileList = new ArrayList<>(Arrays.asList(psiDirectory.getFiles()));
    for (PsiDirectory subDir : psiDirectory.getSubdirectories()) {
      psiFileList.addAll(getFilesRecursively(subDir));
    }
    return psiFileList;
  }

  @Override
  protected long getFileLength(@NotNull Object file) {
    return PDU.toPsiFile(file).getVirtualFile().getLength();
  }

  @Override
  protected String getFileExtention(@NotNull Object file) {
    return PDU.toPsiFile(file).getVirtualFile().getExtension();
  }

  @Override
  protected boolean isGitIgnored(@NotNull Object file) {
    PsiFile psiFile = PDU.toPsiFile(file);
    final VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) return false;
    return (ChangeListManager.getInstance(psiFile.getProject()).isIgnoredFile(virtualFile));
  }
}
