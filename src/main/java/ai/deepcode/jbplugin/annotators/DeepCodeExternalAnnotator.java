package ai.deepcode.jbplugin.annotators;

import ai.deepcode.javaclient.responses.*;
import ai.deepcode.jbplugin.utils.DeepCodeUtils;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeepCodeExternalAnnotator
    extends ExternalAnnotator<GetAnalysisResponse, GetAnalysisResponse> {

  private static final Logger LOG = LoggerFactory.getLogger("DeepCode");

  @Nullable
  @Override
  public GetAnalysisResponse collectInformation(@NotNull PsiFile psiFile) {
    return DeepCodeUtils.getAnalysisResponse(psiFile);
  }

  @Nullable
  @Override
  public GetAnalysisResponse doAnnotate(GetAnalysisResponse collectedInfo) {
    return collectedInfo;
  }

  @Override
  public void apply(
      @NotNull PsiFile psiFile,
      GetAnalysisResponse response,
      @NotNull AnnotationHolder holder) {

    if (!response.getStatus().equals("DONE")) return;
    AnalysisResults analysisResults = response.getAnalysisResults();
    if (analysisResults == null) {
      LOG.error("AnalysisResults is null for: ", response);
      return;
    }
    FileSuggestions fileSuggestions =
        analysisResults.getFiles().get("/" + psiFile.getVirtualFile().getPath());
    if (fileSuggestions == null) return;
    final Suggestions suggestions = analysisResults.getSuggestions();
    if (suggestions == null) {
      LOG.error("Suggestions is empty for: ", response);
      return;
    }
    Document document = psiFile.getViewProvider().getDocument();
    if (document == null) {
      LOG.error("Document not found for: ", psiFile, response);
      return;
    }

    for (String suggestionIndex : fileSuggestions.keySet()) {
      final Suggestion suggestion = suggestions.get(suggestionIndex);
      if (suggestion == null) {
        LOG.error("Suggestion not found for: ", suggestionIndex, response);
        return;
      }

      final String message = suggestion.getMessage();

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
      for (FileRange fileRange : fileSuggestions.get(suggestionIndex)) {
        final int startRow = fileRange.getRows().get(0);
        final int endRow = fileRange.getRows().get(1);
        final int startCol = fileRange.getCols().get(0) - 1; // inclusive
        final int endCol = fileRange.getCols().get(1);
        final int lineStartOffset = document.getLineStartOffset(startRow - 1); // to 0-based
        final int lineEndOffset = document.getLineStartOffset(endRow - 1);
        final TextRange rangeToAnnotate =
            new TextRange(lineStartOffset + startCol, lineEndOffset + endCol);

        holder.newAnnotation(severity, message).range(rangeToAnnotate).create();
      }
    }
  }
}
