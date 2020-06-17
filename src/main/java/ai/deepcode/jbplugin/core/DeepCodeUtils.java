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
import java.util.stream.Collectors;

public final class DeepCodeUtils extends DeepCodeUtilsBase {

  private static final DeepCodeUtils INSTANCE = new DeepCodeUtils();
  private final DCLogger dcLogger = DCLogger.getInstance();

  private DeepCodeUtils() {
    super(
        AnalysisData.getInstance(),
        DeepCodeParams.getInstance(),
        DCLogger.getInstance());
  }

  public static DeepCodeUtilsBase getInstance() {
    return INSTANCE;
  }

  @Override
  public List<Object> getAllSupportedFilesInProject(@NotNull Object projectO) {
    Project project = PDU.toProject(projectO);
    final List<Object> result =
        RunUtils.computeInReadActionInSmartMode(
            project,
            () -> {
              final List<PsiFile> allProjectFiles = allProjectFiles(project);
              if (allProjectFiles.isEmpty()) {
                dcLogger.logWarn("Empty files list for project: " + project);
              }
              // Initial scan for .dcignore files
              allProjectFiles.stream()
                  .filter(DeepCodeIgnoreInfoHolder::is_dcignoreFile)
                  .forEach(DeepCodeIgnoreInfoHolder::update_dcignoreFileContent);
              return allProjectFiles.stream()
                  .filter(this::isSupportedFileFormat)
                  .collect(Collectors.toList());
            });
    if (result.isEmpty()) dcLogger.logWarn("Empty supported files list for project: " + project);
    return result;
  }

  private List<PsiFile> allProjectFiles(@NotNull Project project) {
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
    return getFilesRecursively(prjDirectory);
  }

  private List<PsiFile> getFilesRecursively(@NotNull PsiDirectory psiDirectory) {
    List<PsiFile> psiFileList = new ArrayList<>(Arrays.asList(psiDirectory.getFiles()));
    for (PsiDirectory subDir : psiDirectory.getSubdirectories()) {
      psiFileList.addAll(getFilesRecursively(subDir));
    }
    return psiFileList;
  }

  @Override
  public boolean isSupportedFileFormat(Object file) {
    PsiFile psiFile = PDU.toPsiFile(file);
    // fixme debug only
    // DCLogger.getInstance().info("isSupportedFileFormat started for " + psiFile.getName());
    if (DeepCodeIgnoreInfoHolder.isIgnoredFile(psiFile)) return false;
    final VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) return false;
    if (ChangeListManager.getInstance(psiFile.getProject()).isIgnoredFile(virtualFile))
      return false;
    final boolean result =
        virtualFile.getLength() < MAX_FILE_SIZE
            && (supportedExtensions.contains(virtualFile.getExtension())
                || supportedConfigFiles.contains(virtualFile.getName()));
    // fixme debug only
    // DCLogger.getInstance().info("isSupportedFileFormat ends for " + psiFile.getName());
    return result;
  }
}
