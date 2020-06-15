package ai.deepcode.javaclient.core;

import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class HashContentUtilsBase {

  private final PlatformDependentUtilsBase platformDependentUtils;

  protected HashContentUtilsBase(@NotNull PlatformDependentUtilsBase platformDependentUtils){
    this.platformDependentUtils = platformDependentUtils;
  };
  
  private static final Map<Object, String> mapFile2Hash = new ConcurrentHashMap<>();
  private static final Map<Object, String> mapFile2Content = new ConcurrentHashMap<>();

  public void removeFileHashContent(@NotNull Object file) {
    mapFile2Hash.remove(file);
    mapFile2Content.remove(file);
  }

  void removeProjectHashContent(@NotNull Object project) {
    mapFile2Hash.keySet().removeIf(f -> platformDependentUtils.getProject(f) == project);
    mapFile2Content.keySet().removeIf(f -> platformDependentUtils.getProject(f) == project);
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

  /** check if Hash for Object was changed comparing to cached hash */
  public boolean isHashChanged(@NotNull Object file) {
    // fixme debug only
    // DCLogger.info("hash check started");
    String newHash = doGetHash(doGetFileContent(file));
    String oldHash = mapFile2Hash.put(file, newHash);
    // fixme debug only
/*
    DCLogger.info(
        "Hash check (if file been changed) for "
            + file.getName()
            + "\noldHash = "
            + oldHash
            + "\nnewHash = "
            + newHash);
*/

    return !newHash.equals(oldHash);
  }

  String getHash(@NotNull Object file) {
    return mapFile2Hash.computeIfAbsent(file, this::doGetHash);
  }

  private String doGetHash(@NotNull Object file) {
    return doGetHash(getFileContent(file));
  }

  private String doGetHash(@NotNull String fileText) {
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
  String getFileContent(@NotNull Object file) {
    // potential OutOfMemoryException for too large projects
    return mapFile2Content.computeIfAbsent(file, this::doGetFileContent);
  }

  @NotNull
  protected abstract String doGetFileContent(@NotNull Object file);

}
