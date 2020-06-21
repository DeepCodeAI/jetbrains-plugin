package ai.deepcode.jbplugin.core;

import ai.deepcode.javaclient.core.MyTextRange;
import ai.deepcode.javaclient.core.PlatformDependentUtilsBase;
import ai.deepcode.jbplugin.DeepCodeNotifications;
import ai.deepcode.jbplugin.ui.myTodoView;
import com.intellij.ide.BrowserUtil;
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
import java.util.function.Consumer;
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
  protected String getProjectBasedFilePath(@NotNull Object file) {
    PsiFile psiFile = toPsiFile(file);
    // looks like we don't need ReadAction for this (?)
    String absolutePath = psiFile.getVirtualFile().getPath();
    final String projectPath = psiFile.getProject().getBasePath();
    if (projectPath != null) {
      absolutePath = absolutePath.replace(projectPath, "");
    }
    return absolutePath;
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
      DCLogger.getInstance().logWarn("Document not found for file: " + psiFile);
      return 0;
    }
    return document.getLineStartOffset(line);
  }

  @Override
  public void runInBackgroundCancellable(
      @NotNull Object file, @NotNull String title, @NotNull Consumer<Object> progressConsumer) {
    RunUtils.runInBackgroundCancellable(toPsiFile(file), title, progressConsumer);
  }

  @Override
  public void runInBackground(
      @NotNull Object project, @NotNull String title, @NotNull Consumer<Object> progressConsumer) {
    RunUtils.runInBackground(toProject(project), title, progressConsumer);
  }

  @Override
  public void cancelRunningIndicators(@NotNull Object project) {
    RunUtils.cancelRunningIndicators(toProject(project));
  }

  @Override
  public void doFullRescan(@NotNull Object project) {
    if (!RunUtils.isFullRescanRequested(toProject(project))) {
      RunUtils.rescanInBackgroundCancellableDelayed(toProject(project), DEFAULT_DELAY_SMALL, false);
    }
  }

  @Override
  public void refreshPanel(@NotNull Object project) {
    ServiceManager.getService(toProject(project), myTodoView.class).refresh();
  }

  @Override
  public boolean isLogged(@Nullable Object project, boolean userActionNeeded) {
    return LoginUtils.getInstance()
        .isLogged(project == null ? null : toProject(project), userActionNeeded);
  }

  @Override
  public void progressSetText(@Nullable Object progress, String text) {
    if (progress instanceof ProgressIndicator) {
      ((ProgressIndicator) progress).setText(text);
    }
  }

  @Override
  public void progressCheckCanceled(@Nullable Object progress) {
    if (progress instanceof ProgressIndicator) {
      ((ProgressIndicator) progress).checkCanceled();
    }
  }

  @Override
  public void progressSetFraction(@Nullable Object progress, double fraction) {
    if (progress instanceof ProgressIndicator) {
      ((ProgressIndicator) progress).setFraction(fraction);
    }
  }

  @Override
  public void showInBrowser(@NotNull String url) {
    BrowserUtil.open(DeepCodeParams.getInstance().getLoginUrl());
  }

  @Override
  public void showLoginLink(Object project, String message) {
    DeepCodeNotifications.showLoginLink(project == null ? null : toProject(project), message);
  }

  @Override
  public void showConsentRequest(Object project, boolean userActionNeeded) {
    DeepCodeNotifications.showConsentRequest(toProject(project), userActionNeeded);
  }

  @Override
  public void showInfo(String message, Object project) {
    DeepCodeNotifications.showInfo(message, toProject(project));
  }

  @Override
  public void showWarn(String message, Object project) {
    DeepCodeNotifications.showWarn(message, toProject(project));
  }

  @Override
  public void showError(String message, Object project) {
    DeepCodeNotifications.showError(message, toProject(project));
  }
}
