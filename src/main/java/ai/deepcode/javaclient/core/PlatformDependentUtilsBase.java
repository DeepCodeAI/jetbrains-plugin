package ai.deepcode.javaclient.core;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PlatformDependentUtilsBase {

  @NotNull
  abstract Object getProject(@NotNull Object file);

  @NotNull
  abstract String getProjectName(@NotNull Object project);

  @NotNull
  abstract String getFileName(@NotNull Object file);

  @NotNull
  abstract String getDeepCodedFilePath(@NotNull Object file);

  abstract long getFileSize(@NotNull Object file);

  abstract int getLineStartOffset(@NotNull Object file, int line);


  abstract void runInBackgroundCancellable(@NotNull Object file, @NotNull Runnable runnable);

  abstract void cancelRunningIndicators(@NotNull Object project);

  abstract void doFullRescan(@NotNull Object project);


  abstract void refreshPanel(@NotNull Object project);

  abstract boolean isLogged(@Nullable Object project, boolean userActionNeeded);


  abstract void progressSetText(String text);

  abstract void progressCheckCanceled();

  abstract void progressSetFraction(double fraction);
}
