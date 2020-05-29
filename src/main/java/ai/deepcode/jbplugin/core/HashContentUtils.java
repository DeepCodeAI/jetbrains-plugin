package ai.deepcode.jbplugin.core;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HashContentUtils {
  private static final Map<PsiFile, String> mapPsiFile2Hash = new ConcurrentHashMap<>();
  private static final Map<PsiFile, String> mapPsiFile2Content = new ConcurrentHashMap<>();

  static void removeHashContent(@NotNull PsiFile psiFile) {
    mapPsiFile2Hash.remove(psiFile);
    mapPsiFile2Content.remove(psiFile);
  }

  static void removeHashContent(@NotNull Project project) {
    mapPsiFile2Hash.keySet().removeIf(f -> f.getProject() == project);
    mapPsiFile2Content.keySet().removeIf(f -> f.getProject() == project);
  }

  // ?? com.intellij.openapi.util.text.StringUtil.toHexString
  // https://www.baeldung.com/sha-256-hashing-java#message-digest
  private static String bytesToHex(byte[] hash) {
    StringBuilder hexString = new StringBuilder();
    for (byte b : hash) {
      String hex = Integer.toHexString(0xff & b);
      if (hex.length() == 1) hexString.append('0');
      hexString.append(hex);
    }
    return hexString.toString();
  }

  /** check if Hash for PsiFile was changed comparing to cached hash */
  public static boolean isHashChanged(@NotNull PsiFile psiFile) {
    // fixme debug only
    // DCLogger.info("hash check started");
    String newHash = doGetHash(doGetFileContent(psiFile));
    String oldHash = mapPsiFile2Hash.put(psiFile, newHash);
    // fixme debug only
    DCLogger.info(
        "Hash check (if file been changed) for "
            + psiFile.getName()
            + "\noldHash = "
            + oldHash
            + "\nnewHash = "
            + newHash);

    return !newHash.equals(oldHash);
  }

  static String getHash(@NotNull PsiFile psiFile) {
    return mapPsiFile2Hash.computeIfAbsent(psiFile, HashContentUtils::doGetHash);
  }

  private static String doGetHash(@NotNull PsiFile psiFile) {
    return doGetHash(getFileContent(psiFile));
  }

  private static String doGetHash(@NotNull String fileText) {
    MessageDigest messageDigest;
    try {
      messageDigest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
    byte[] encodedHash = messageDigest.digest(fileText.getBytes(StandardCharsets.UTF_8));
    return bytesToHex(encodedHash);
  }

  @NotNull
  static String getFileContent(@NotNull PsiFile psiFile) {
    // potential OutOfMemoryException for too large projects
    return mapPsiFile2Content.computeIfAbsent(psiFile, HashContentUtils::doGetFileContent);
  }

  @NotNull
  private static String doGetFileContent(@NotNull PsiFile psiFile) {
    // psiFile.getText() is NOT expensive as it's goes to VirtualFileContent.getText()
    return RunUtils.computeInReadActionInSmartMode(
        psiFile.getProject(), () -> getPsiFileText(psiFile));
  }

  /** Should be run inside <b>Read action</b> !!! */
  @NotNull
  private static String getPsiFileText(@NotNull PsiFile psiFile) {
    if (!psiFile.isValid()) {
      DCLogger.warn("Invalid PsiFile: " + psiFile);
      return "";
    }
    return psiFile.getText();
  }
}
