package ai.deepcode.jbplugin.core;

import ai.deepcode.javaclient.core.DeepCodeIgnoreInfoHolderBase;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

class DeepCodeIgnoreInfoHolder extends DeepCodeIgnoreInfoHolderBase {

  private static final DeepCodeIgnoreInfoHolder INSTANCE = new DeepCodeIgnoreInfoHolder();

  public static DeepCodeIgnoreInfoHolder getInstance() {
    return INSTANCE;
  }

  private DeepCodeIgnoreInfoHolder() {
    super(HashContentUtils.getInstance());
  }

  @Override
  protected String getFilePath(@NotNull Object file) {
    return PDU.toPsiFile(file).getVirtualFile().getPath();
  }

  @Override
  protected boolean inScope(@NotNull Object dcignoreFile, @NotNull Object fileToCheck) {
    final VirtualFile dcignoreDir = PDU.toPsiFile(dcignoreFile).getVirtualFile().getParent();
    return VfsUtil.isAncestor(dcignoreDir, PDU.toPsiFile(fileToCheck).getVirtualFile(), true);
  }

  @Override
  protected String getFileName(@NotNull Object file) {
    return PDU.toPsiFile(file).getVirtualFile().getName();
  }

  @Override
  protected String getDirPath(@NotNull Object file) {
    return PDU.toPsiFile(file).getVirtualFile().getParent().getPath();
  }
}
