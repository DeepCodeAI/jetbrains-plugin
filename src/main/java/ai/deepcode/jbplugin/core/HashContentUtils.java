package ai.deepcode.jbplugin.core;

import ai.deepcode.javaclient.core.HashContentUtilsBase;
import ai.deepcode.javaclient.core.PlatformDependentUtilsBase;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class HashContentUtils extends HashContentUtilsBase {

  private static final HashContentUtils INSTANCE = new HashContentUtils(PDU.getInstance());

  private HashContentUtils(@NotNull PlatformDependentUtilsBase platformDependentUtils) {
    super(platformDependentUtils);
  }

  public static HashContentUtils getInstance() {
    return INSTANCE;
  }

  @NotNull
  public String doGetFileContent(@NotNull Object file) {
    PsiFile psiFile = PDU.toPsiFile(file);
    final String fileContent =
        RunUtils.computeInReadActionInSmartMode(
            psiFile.getProject(), () -> getPsiFileText(psiFile));
    return fileContent != null ? fileContent : "";
  }

  /** Should be run inside <b>Read action</b> !!! */
  @NotNull
  private static String getPsiFileText(@NotNull PsiFile psiFile) {
    if (!psiFile.isValid()) {
      DCLogger.getInstance().logWarn("Invalid PsiFile: " + psiFile);
      return "";
    }
    // psiFile.getText() is NOT expensive as it's goes to VirtualFileContent.getText()
    return psiFile.getText();
  }
}
