package ai.deepcode.jbplugin.ui.utils;

import ai.deepcode.jbplugin.utils.AnalysisData;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.PsiFile;
import com.intellij.ui.HighlightedRegion;
import com.intellij.ui.JBColor;
import com.intellij.ui.SearchTextField;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DeepCodeUIUtils {
  private DeepCodeUIUtils() {}

  private static final TextAttributes ERROR_ATTRIBUTES = createAttributes(JBColor.RED);
  private static final TextAttributes WARNING_ATTRIBUTES = createAttributes(Color.YELLOW);
  private static final TextAttributes INFO_ATTRIBUTES = createAttributes(JBColor.BLUE);

  private static TextAttributes createAttributes(Color frgColor) {
    TextAttributes result =
        EditorColorsManager.getInstance()
            .getGlobalScheme()
            .getAttributes(DefaultLanguageHighlighterColors.IDENTIFIER)
            .clone();
    result.setForegroundColor(frgColor);
    return result;
  }

  public static String addErrWarnInfoCounts(
      @NotNull Collection<PsiFile> psiFiles,
      String originalMsg,
      boolean withTextEWI,
      @NotNull List<HighlightedRegion> regionsToUpdate) {
    int errors = 0;
    int warnings = 0;
    int infos = 0;
    Set<String> countedSuggestions = new HashSet<>();
    for (PsiFile file : psiFiles) {
      for (AnalysisData.SuggestionForFile suggestion : AnalysisData.getAnalysis(file)) {
        if (!countedSuggestions.contains(suggestion.getId())) {
          final int severity = suggestion.getSeverity();
          if (severity == 1) infos += 1;
          else if (severity == 2) warnings += 1;
          else if (severity == 3) errors += 1;
          countedSuggestions.add(suggestion.getId());
        }
      }
    }
    if (errors == 0 && infos == 0 && warnings == 0) return originalMsg;

    String result = originalMsg + ": ";
    regionsToUpdate.clear();
    if (errors != 0) {
      int oldLength = result.length();
      result += "\u2BBF " + errors + (withTextEWI ? " errors" : "") + " ";
      regionsToUpdate.add(
          new HighlightedRegion(oldLength, result.length(), ERROR_ATTRIBUTES));
    }
    if (warnings != 0) {
      int oldLength = result.length();
      result += "\u26A0 " + warnings + (withTextEWI ? " warnings" : "") + " ";
      regionsToUpdate.add(
          new HighlightedRegion(oldLength, result.length(), WARNING_ATTRIBUTES));
    }
    if (infos != 0) {
      int oldLength = result.length();
      result += "\u24D8 " + infos + (withTextEWI ? " informational" : "") + " ";
      regionsToUpdate.add(
          new HighlightedRegion(oldLength, result.length(), INFO_ATTRIBUTES));
    }
    return result;
  }
}
