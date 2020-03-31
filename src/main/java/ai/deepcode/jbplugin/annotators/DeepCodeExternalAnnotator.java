package ai.deepcode.jbplugin.annotators;

import ai.deepcode.jbplugin.utils.DeepCodeParams;
import ai.deepcode.jbplugin.utils.AnalysisData;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class DeepCodeExternalAnnotator
    extends ExternalAnnotator<
        List<AnalysisData.SuggestionForFile>, List<AnalysisData.SuggestionForFile>> {

  private static final Logger LOG = LoggerFactory.getLogger("DeepCode.Annotator");

  @Nullable
  @Override
  public List<AnalysisData.SuggestionForFile> collectInformation(@NotNull PsiFile psiFile) {
    if (!DeepCodeParams.isSupportedFileFormat(psiFile)) return null;
    return AnalysisData.getAnalysis(psiFile);
  }

  @Override
  @Nullable
  public List<AnalysisData.SuggestionForFile> collectInformation(
      @NotNull PsiFile psiFile, @NotNull Editor editor, boolean hasErrors) {
    return collectInformation(psiFile);
  }

  @Nullable
  @Override
  public List<AnalysisData.SuggestionForFile> doAnnotate(
      List<AnalysisData.SuggestionForFile> collectedInfo) {
    return collectedInfo;
  }

  @Override
  public void apply(
      @NotNull PsiFile psiFile,
      List<AnalysisData.SuggestionForFile> suggestions,
      @NotNull AnnotationHolder holder) {
    for (AnalysisData.SuggestionForFile suggestion : suggestions) {
      HighlightSeverity severity;
      switch (suggestion.getSeverity()) {
        case 1:
          severity = HighlightSeverity.WEAK_WARNING;
          break;
        case 2:
          severity = HighlightSeverity.WARNING;
          break;
        case 3:
          severity = HighlightSeverity.ERROR;
          break;
        default:
          severity = HighlightSeverity.INFORMATION;
          break;
      }
      for (TextRange range : suggestion.getRanges()) {
        holder
            .newAnnotation(severity, "DeepCode: " + suggestion.getMessage())
            .range(range)
            .create();
      }
    }
  }
}
