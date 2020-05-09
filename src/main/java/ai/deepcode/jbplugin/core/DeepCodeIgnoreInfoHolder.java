package ai.deepcode.jbplugin.core;

import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

class DeepCodeIgnoreInfoHolder {

  private DeepCodeIgnoreInfoHolder() {}

  private static final Map<PsiFile, Set<String>> map_dcignore2Regexps = new ConcurrentHashMap<>();

  static boolean isIgnoredFile(PsiFile file) {
    final VirtualFile fileToCheck = file.getVirtualFile();
    final String filePath = fileToCheck.getPath();
    return map_dcignore2Regexps.entrySet().stream()
        .filter(e -> inScope(e.getKey().getVirtualFile(), fileToCheck))
        .flatMap(e -> e.getValue().stream())
        .anyMatch(filePath::matches);
  }

  private static boolean inScope(
      @NotNull VirtualFile dcignoreFile, @NotNull VirtualFile fileToCheck) {
    final VirtualFile dcignoreDir = dcignoreFile.getParent();
    return VfsUtil.isAncestor(dcignoreDir, fileToCheck, true);
  }

  static boolean is_ignoreFile(@NotNull PsiFile file) {
    return is_dcignoreFile(file) || file.getVirtualFile().getName().equals(".gitignore");
  }

  static boolean is_dcignoreFile(@NotNull PsiFile file) {
    return file.getVirtualFile().getName().equals(".dcignore");
  }

  static void remove_dcignoreFileContent(@NotNull PsiFile file) {
    map_dcignore2Regexps.remove(file);
  }

  static void update_dcignoreFileContent(@NotNull PsiFile file) {
    // map_dcignore2Regexps.remove(file);
    map_dcignore2Regexps.put(file, parse_dcignoreFile2Regexps(file));
  }

  private static Set<String> parse_dcignoreFile2Regexps(@NotNull PsiFile file) {
    Set<String> result = new HashSet<>();
    final VirtualFile virtualFile = file.getVirtualFile();
    String basePath = virtualFile.getParent().getPath();
    String lineSeparator = "[\n\r]";
    final String fileText =
        DeepCodeUtils.computeInReadActionInSmartMode(file.getProject(), file::getText);
    for (String line : fileText.split(lineSeparator)) {

      // https://git-scm.com/docs/gitignore#_pattern_format
      // todo: `!` negation not implemented yet
      line = line.trim();
      if (line.isEmpty() || line.startsWith("#")) continue;

      String prefix = basePath;
      // If there is a separator at the beginning or middle (or both) of the pattern, then the
      // pattern is relative to the directory level of the particular .gitignore file itself.
      // Otherwise the pattern may also match at any level below the .gitignore level.
      if (line.substring(0, line.length() - 1).indexOf('/') == -1) prefix += ".*";

      // If there is a separator at the end of the pattern then the pattern will only match
      // directories, otherwise the pattern can match both files and directories.
      String postfix =
          (line.endsWith("/")
                  // A trailing "/**" matches everything inside. For example, "abc/**" matches all
                  // files inside directory "abc", relative to the location of the .gitignore file,
                  // with infinite depth.
                  || line.endsWith("/**"))
              ? ".+"
              : (line.lastIndexOf('.') == -1) ? "/.+" : "";

      String body =
          line.replace(".", "\\.")
              // An asterisk "*" matches anything except a slash.
              .replace("*", "[^/]*")
              // The character "?" matches any one character except "/".
              .replace("?", "[^/]?")
              // A slash followed by two consecutive asterisks then a slash matches zero or more
              // directories. For example, "a/**/b" matches "a/b", "a/x/b", "a/x/y/b" and so on.
              .replace("[^/]*[^/]*", ".*");

      result.add(prefix + body + postfix);
    }
    return result;
  }
}
