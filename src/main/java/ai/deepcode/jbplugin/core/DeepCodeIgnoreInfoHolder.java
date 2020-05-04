package ai.deepcode.jbplugin.core;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

class DeepCodeIgnoreInfoHolder {

  private DeepCodeIgnoreInfoHolder() {}

  private static final Map<PsiFile, Set<String>> map_dcignore2Regexps = new HashMap<>();

  static boolean isIgnoredFile(PsiFile file) {
    final String filePath = file.getVirtualFile().getPath();
    return map_dcignore2Regexps.entrySet().stream()
        .flatMap(e -> e.getValue().stream())
        .anyMatch(filePath::matches);
  }

  static boolean is_dcignoreFile(@NotNull PsiFile file) {
    return file.getVirtualFile().getName().equals(".dcignore");
  }

  static void remove_dcignoreFileContent(@NotNull PsiFile file) {
    map_dcignore2Regexps.remove(file);
  }

  static void update_dcignoreFileContent(@NotNull PsiFile file) {
    map_dcignore2Regexps.remove(file);
    map_dcignore2Regexps.put(file, parse_dcignoreFile2Regexps(file));
  }

  private static Set<String> parse_dcignoreFile2Regexps(@NotNull PsiFile file) {
    Set<String> result = new HashSet<>();
    final VirtualFile virtualFile = file.getVirtualFile();
    String basePath = virtualFile.getParent().getPath();
    String lineSeparator = "[\n\r]";
    for (String line : file.getText().split(lineSeparator)) {
      if (line.isEmpty() || line.startsWith("#")) continue;
      String regexp =
          ".*/" + line.replace(".", "\\.").replace("*", ".*") + (line.endsWith("/") ? ".*" : "");
      result.add(regexp);
    }
    return result;
  }
}
