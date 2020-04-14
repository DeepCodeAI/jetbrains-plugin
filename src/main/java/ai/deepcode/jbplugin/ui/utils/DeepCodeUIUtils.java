package ai.deepcode.jbplugin.ui.utils;

import ai.deepcode.jbplugin.utils.AnalysisData;
import ai.deepcode.jbplugin.utils.DeepCodeUtils;
import com.intellij.execution.process.ConsoleHighlighter;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.ui.HighlightedRegion;
import com.intellij.ui.JBColor;
import com.intellij.ui.RowIcon;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.font.TextAttribute;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DeepCodeUIUtils {
  private DeepCodeUIUtils() {}

  private static final TextAttributes ERROR_ATTRIBUTES = createAttributes(ConsoleHighlighter.RED);
  private static final TextAttributes WARNING_ATTRIBUTES =
      createAttributes(ConsoleHighlighter.YELLOW);
  private static final TextAttributes INFO_ATTRIBUTES = createAttributes(ConsoleHighlighter.BLUE);

  private static TextAttributes createAttributes(TextAttributesKey textAttributesKey) {
    return EditorColorsManager.getInstance()
        .getGlobalScheme()
        .getAttributes(textAttributesKey)
        .clone();
  }

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
    DeepCodeUtils.ErrorsWarningsInfos ewi = DeepCodeUtils.getEWI(psiFiles);
    int errors = ewi.getErrors();
    int warnings = ewi.getWarnings();
    int infos = ewi.getInfos();
    if (errors == 0 && infos == 0 && warnings == 0) return originalMsg;

    String result = originalMsg + ": ";
    regionsToUpdate.clear();
    if (errors != 0) {
      int oldLength = result.length();
      result += "\u2BBF " + errors + (withTextEWI ? " errors" : "") + " ";
      regionsToUpdate.add(new HighlightedRegion(oldLength, result.length(), ERROR_ATTRIBUTES));
    }
    if (warnings != 0) {
      int oldLength = result.length();
      result += "\u26A0 " + warnings + (withTextEWI ? " warnings" : "") + " ";
      regionsToUpdate.add(new HighlightedRegion(oldLength, result.length(), WARNING_ATTRIBUTES));
    }
    if (infos != 0) {
      int oldLength = result.length();
      result += "\u24D8 " + infos + (withTextEWI ? " informational" : "") + " ";
      regionsToUpdate.add(new HighlightedRegion(oldLength, result.length(), INFO_ATTRIBUTES));
    }
    return result;
  }

  public static final Icon EMPTY_EWI_ICON =
      new RowIcon(
          AllIcons.Nodes.WarningIntroduction,
          IconUtil.textToIcon("0", new JLabel(), JBUIScale.scale(12f)),
          AllIcons.General.ShowWarning,
          IconUtil.textToIcon("0", new JLabel(), JBUIScale.scale(12f)),
          AllIcons.General.ShowInfos,
          IconUtil.textToIcon("0", new JLabel(), JBUIScale.scale(12f)));

  public static Icon getSummaryIcon(@NotNull Project project) {
    DeepCodeUtils.ErrorsWarningsInfos ewi =
        DeepCodeUtils.getEWI(AnalysisData.getAllFilesWithSuggestions(project));
    int errors = ewi.getErrors();
    int warnings = ewi.getWarnings();
    int infos = ewi.getInfos();
    if (errors == 0 && infos == 0 && warnings == 0) return EMPTY_EWI_ICON;

    Icon errorIcon = (errors != 0) ? AllIcons.General.Error : AllIcons.Nodes.WarningIntroduction;
    Icon warningIcon = (warnings != 0) ? AllIcons.General.Warning : AllIcons.General.ShowWarning;
    Icon infoIcon = (infos != 0) ? AllIcons.General.Information : AllIcons.General.ShowInfos;

    return new RowIcon(
        errorIcon,
        IconUtil.textToIcon(String.valueOf(errors), new JLabel(), JBUIScale.scale(12f)),
        warningIcon,
        IconUtil.textToIcon(String.valueOf(warnings), new JLabel(), JBUIScale.scale(12f)),
        infoIcon,
        IconUtil.textToIcon(String.valueOf(infos), new JLabel(), JBUIScale.scale(12f)));
  }
}
