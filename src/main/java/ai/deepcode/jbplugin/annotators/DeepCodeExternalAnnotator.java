package ai.deepcode.jbplugin.annotators;

import ai.deepcode.jbplugin.ui.myTodoView;
import ai.deepcode.jbplugin.utils.DeepCodeParams;
import ai.deepcode.jbplugin.utils.AnalysisData;
import ai.deepcode.jbplugin.utils.DeepCodeUtils;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

public class DeepCodeExternalAnnotator
    extends ExternalAnnotator<PsiFile, List<AnalysisData.SuggestionForFile>> {

  private static final Logger LOG = LoggerFactory.getLogger("DeepCode.Annotator");

  @Nullable
  @Override
  public PsiFile collectInformation(@NotNull PsiFile psiFile) {
    return psiFile;
  }

  @Override
  @Nullable
  public PsiFile collectInformation(
      @NotNull PsiFile psiFile, @NotNull Editor editor, boolean hasErrors) {
    return collectInformation(psiFile);
  }

  @Nullable
  @Override
  public List<AnalysisData.SuggestionForFile> doAnnotate(PsiFile psiFile) {
    if (!DeepCodeParams.isSupportedFileFormat(psiFile)) return Collections.emptyList();
    return AnalysisData.getAnalysis(psiFile);
  }

  @SuppressWarnings("deprecation") // later move to .newAnnotation introduced in 2020.1
  @Override
  public void apply(
      @NotNull PsiFile psiFile,
      List<AnalysisData.SuggestionForFile> suggestions,
      @NotNull AnnotationHolder holder) {
    if (suggestions == null) return;
    for (AnalysisData.SuggestionForFile suggestion : suggestions) {
/*
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
*/
      for (TextRange range : suggestion.getRanges()) {
        switch (suggestion.getSeverity()) {
          case 1:
            holder.createWeakWarningAnnotation(range, "DeepCode: " + suggestion.getMessage());
            break;
          case 2:
            holder.createWarningAnnotation(range, "DeepCode: " + suggestion.getMessage());
            break;
          case 3:
            holder.createErrorAnnotation(range, "DeepCode: " + suggestion.getMessage());
            break;
          default:
            holder.createInfoAnnotation(range, "DeepCode: " + suggestion.getMessage());
            break;
        }
/*
        holder
            .newAnnotation(severity, "DeepCode: " + suggestion.getMessage())
            .range(range)
            .create();
*/
      }
    }
    // fixme
    // Update CurrentFile Panel if file was edited
    ServiceManager.getService(psiFile.getProject(), myTodoView.class).refresh();
//    DeepCodeUtils.asyncUpdateCurrentFilePanel(psiFile);
  }

}
