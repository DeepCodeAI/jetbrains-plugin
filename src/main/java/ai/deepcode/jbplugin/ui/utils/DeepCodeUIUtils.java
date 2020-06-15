package ai.deepcode.jbplugin.ui.utils;

import ai.deepcode.jbplugin.core.AnalysisData;
import ai.deepcode.jbplugin.core.DCLogger;
import ai.deepcode.jbplugin.core.DeepCodeUtils;
import ai.deepcode.jbplugin.core.PDU;
import com.intellij.execution.process.ConsoleHighlighter;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.ui.HighlightedRegion;
import com.intellij.ui.RowIcon;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.List;

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
      result += "\u24BE " + errors + (withTextEWI ? " errors" : "") + " ";
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

  private static final float fontToScale = JBUIScale.scale(12f);

  private static Icon scaleIcon(Icon sourceIcon) {
    return IconUtil.scaleByFont(sourceIcon, null, fontToScale - 2);
  }

  private static final Icon errorGray = scaleIcon(AllIcons.Nodes.WarningIntroduction);
  private static final Icon errorColor = scaleIcon(AllIcons.General.Error);
  private static final Icon warningGray = scaleIcon(AllIcons.General.ShowWarning);
  private static final Icon warningColor = scaleIcon(AllIcons.General.Warning);
  private static final Icon infoGray = scaleIcon(AllIcons.General.Note);
  private static final Icon infoColor = scaleIcon(AllIcons.General.Information);

  public static final Icon EMPTY_EWI_ICON =
      new RowIcon(
          errorGray,
          IconUtil.textToIcon("?", new JLabel(), fontToScale),
          warningGray,
          IconUtil.textToIcon("?", new JLabel(), fontToScale),
          infoGray,
          IconUtil.textToIcon("?", new JLabel(), fontToScale));

  public static Icon getSummaryIcon(@NotNull Project project) {
    if (AnalysisData.getInstance().isAnalysisResultsNOTAvailable(project)) {
      DCLogger.info("EMPTY icon set");
      return EMPTY_EWI_ICON;
    }

    DeepCodeUtils.ErrorsWarningsInfos ewi =
        DeepCodeUtils.getEWI(
            // fixme
            PDU.toPsiFiles(AnalysisData.getInstance().getAllFilesWithSuggestions(project)));
    int errors = ewi.getErrors();
    int warnings = ewi.getWarnings();
    int infos = ewi.getInfos();
    DCLogger.info("error=" + errors + " warning=" + warnings + " info=" + infos);

    return new RowIcon(
        (errors != 0) ? errorColor : errorGray,
        number2ColoredIcon(errors, (errors != 0) ? ERROR_ATTRIBUTES.getForegroundColor() : null),
        (warnings != 0) ? warningColor : warningGray,
        number2ColoredIcon(
            warnings, (warnings != 0) ? WARNING_ATTRIBUTES.getForegroundColor() : null),
        (infos != 0) ? infoColor : infoGray,
        number2ColoredIcon(infos, (infos != 0) ? INFO_ATTRIBUTES.getForegroundColor() : null));
  }

  private static Icon number2ColoredIcon(int number, @Nullable Color color) {
    Icon greyIcon = IconUtil.textToIcon(String.valueOf(number), new JLabel(), fontToScale);
    return greyIcon; // (color == null) ? greyIcon : IconUtil.colorize(greyIcon, color);
  }
}
