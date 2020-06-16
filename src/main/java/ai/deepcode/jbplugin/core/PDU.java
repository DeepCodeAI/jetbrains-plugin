package ai.deepcode.jbplugin.core;

import ai.deepcode.javaclient.core.MyTextRange;
import ai.deepcode.javaclient.core.PlatformDependentUtilsBase;
import ai.deepcode.jbplugin.ui.myTodoView;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.stream.Collectors;

public class PDU extends PlatformDependentUtilsBase {

  private PDU() {};

  private static final PDU INSTANCE = new PDU();

  public static PDU getInstance() {
    return INSTANCE;
  }

  @NotNull
  public static PsiFile toPsiFile(@NotNull Object file) {
    if (!(file instanceof PsiFile))
      throw new IllegalArgumentException("file should be PsiFile instance");
    return (PsiFile) file;
  }

  @NotNull
  public static Collection<PsiFile> toPsiFiles(@NotNull Collection<Object> files) {
    return files.stream().map(PDU::toPsiFile).collect(Collectors.toSet());
  }

  @NotNull
  public static Collection<Object> toObjects(@NotNull Collection<PsiFile> files) {
    return new HashSet<>(files);
  }

  @NotNull
  public static Project toProject(@NotNull Object project) {
    if (!(project instanceof Project))
      throw new IllegalArgumentException("project should be Project instance");
    return (Project) project;
  }

  public static TextRange toTextRange(@NotNull MyTextRange myTextRange) {
    return new TextRange(myTextRange.getStart(), myTextRange.getEnd());
  }

  @Override
  @NotNull
  public Object getProject(@NotNull Object file) {
    return toPsiFile(file).getProject();
  }

  @Override
  @NotNull
  public String getProjectName(@NotNull Object project) {
    return toProject(project).getName();
  }

  @Override
  @NotNull
  public String getFileName(@NotNull Object file) {
    return toPsiFile(file).getVirtualFile().getName();
  }

  @Override
  @NotNull
  public String getDeepCodedFilePath(@NotNull Object file) {
    return DeepCodeUtils.getDeepCodedFilePath(toPsiFile(file));
  }

  @Override
  public long getFileSize(@NotNull Object file) {
    return toPsiFile(file).getVirtualFile().getLength();
  }

  @Override
  public int getLineStartOffset(@NotNull Object file, int line) {
    PsiFile psiFile = toPsiFile(file);
    Document document =
        RunUtils.computeInReadActionInSmartMode(
            psiFile.getProject(), psiFile.getViewProvider()::getDocument);
    if (document == null) {
      DCLogger.getInstance().getInstance().logWarn("Document not found for file: " + psiFile);
      return 0;
    }
    return document.getLineStartOffset(line);
  }

  @Override
  public void runInBackgroundCancellable(@NotNull Object file, @NotNull Runnable runnable) {
    RunUtils.runInBackgroundCancellable(toPsiFile(file), runnable);
  }

  @Override
  public void cancelRunningIndicators(@NotNull Object project) {
    RunUtils.cancelRunningIndicators(toProject(project));
  }

  @Override
  public void doFullRescan(@NotNull Object project) {
    if (!RunUtils.isFullRescanRequested(toProject(project))) {
      RunUtils.rescanInBackgroundCancellableDelayed(toProject(project), RunUtils.DEFAULT_DELAY_SMALL, false);
    }
  }

  @Override
  public void refreshPanel(@NotNull Object project) {
    ServiceManager.getService(toProject(project), myTodoView.class).refresh();
  }

  @Override
  public boolean isLogged(@Nullable Object project, boolean userActionNeeded) {
    return LoginUtils.isLogged(project == null ? null : toProject(project), userActionNeeded);
  }

  @Override
  public void progressSetText(String text) {
    ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    progress.setText(text);
  }

  @Override
  public void progressCheckCanceled() {
    ProgressManager.checkCanceled();
  }

  @Override
  public void progressSetFraction(double fraction) {
    ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    progress.setFraction(fraction);
  }
}
