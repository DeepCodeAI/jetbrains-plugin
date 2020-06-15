package ai.deepcode.javaclient.core;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PlatformDependentUtilsBase {

  @NotNull
  public abstract Object getProject(@NotNull Object file);

  @NotNull
  public abstract String getProjectName(@NotNull Object project);

  @NotNull
  public abstract String getFileName(@NotNull Object file);

  @NotNull
  public abstract String getDeepCodedFilePath(@NotNull Object file);

  public abstract long getFileSize(@NotNull Object file);

  public abstract int getLineStartOffset(@NotNull Object file, int line);


  public abstract void runInBackgroundCancellable(@NotNull Object file, @NotNull Runnable runnable);

  public abstract void cancelRunningIndicators(@NotNull Object project);

  public abstract void doFullRescan(@NotNull Object project);


  public abstract void refreshPanel(@NotNull Object project);

  public abstract boolean isLogged(@Nullable Object project, boolean userActionNeeded);


  public abstract void progressSetText(String text);

  public abstract void progressCheckCanceled();

  public abstract void progressSetFraction(double fraction);
}
