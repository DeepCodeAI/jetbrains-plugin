package ai.deepcode.jbplugin.annotators;

import ai.deepcode.javaclient.core.MyTextRange;
import ai.deepcode.jbplugin.actions.DeepCodeIntentionAction;
import ai.deepcode.jbplugin.core.DCLogger;
import ai.deepcode.jbplugin.core.PDU;
import ai.deepcode.jbplugin.core.AnalysisData;
import ai.deepcode.jbplugin.core.DeepCodeUtils;
import ai.deepcode.javaclient.core.SuggestionForFile;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * We should use ExternalAnnotator to work on <b>files level</b> while ordinary Annotators works on
 * elements level.
 */
public class DeepCodeExternalAnnotator extends ExternalAnnotator<PsiFile, List<SuggestionForFile>> {

  private static final Logger LOG = LoggerFactory.getLogger("DeepCode.Annotator");

/*
  @Nullable
  @Override
  public PsiFile collectInformation(@NotNull PsiFile psiFile) {
    DCLogger.getInstance().info("collectInformation(@NotNull PsiFile psiFile) for " + psiFile);
    return psiFile;
  }

*/
  @Override
  @Nullable
  public PsiFile collectInformation(
      @NotNull PsiFile psiFile, @NotNull Editor editor, boolean hasErrors) {
    //DCLogger.getInstance().info("collectInformation(@NotNull PsiFile psiFile, @NotNull Editor editor, boolean hasErrors) for " + psiFile);
    return psiFile;
    //return collectInformation(psiFile);
  }

  @Nullable
  @Override
  public List<SuggestionForFile> doAnnotate(PsiFile psiFile) {
    if (!DeepCodeUtils.getInstance().isSupportedFileFormat(psiFile)) return Collections.emptyList();
    final long annotatorId = System.currentTimeMillis();
    DCLogger.getInstance().logInfo("Annotator (" + annotatorId + ") requested for file: " + psiFile.getName());
    AnalysisData.getInstance().waitForUpdateAnalysisFinish(ProgressManager.getInstance().getProgressIndicator());
    ProgressManager.checkCanceled();
    List<SuggestionForFile> suggestions = AnalysisData.getInstance().getAnalysis(psiFile);
    DCLogger.getInstance().logInfo(
        "Annotator (" + annotatorId + ") suggestions gotten for file: " + psiFile.getName());
    ProgressManager.checkCanceled();

    return suggestions;
    /*
        // https://youtrack.jetbrains.com/issue/IDEA-239960
        return ProgressManager.getInstance()
            .runProcess(
                () -> AnalysisData.getAnalysis(psiFile),
                new BackgroundableProcessIndicator(
                    psiFile.getProject(),
                    "DeepCode: Analysing " + psiFile.getName() + " file... ",
                    () -> true,
                    "Stop",
                    "Stop file analysis",
                    true));
    */
  }

  @SuppressWarnings("deprecation") // later move to .newAnnotation introduced in 2020.1
  @Override
  public void apply(
      @NotNull PsiFile psiFile,
      List<SuggestionForFile> suggestions,
      @NotNull AnnotationHolder holder) {
    if (suggestions == null) return;
    for (SuggestionForFile suggestion : suggestions) {
      final String message = "DeepCode: " + suggestion.getMessage();
      Annotation annotation;
      for (MyTextRange myTextRange : suggestion.getRanges()) {
        final TextRange range = PDU.toTextRange(myTextRange);
        switch (suggestion.getSeverity()) {
          case 1:
            annotation = holder.createWeakWarningAnnotation(range, message);
            break;
          case 2:
            annotation = holder.createWarningAnnotation(range, message);
            break;
          case 3:
            annotation = holder.createErrorAnnotation(range, message);
            break;
          default:
            annotation = holder.createInfoAnnotation(range, message);
            break;
        }
        annotation.registerFix(
            new DeepCodeIntentionAction(psiFile, range, suggestion.getRule(), false));
        annotation.registerFix(
            new DeepCodeIntentionAction(psiFile, range, suggestion.getRule(), true));
        /*
                holder
                    .newAnnotation(severity, "DeepCode: " + suggestion.getMessage())
                    .range(range)
                    .create();
        */
      }
    }
  }
}
