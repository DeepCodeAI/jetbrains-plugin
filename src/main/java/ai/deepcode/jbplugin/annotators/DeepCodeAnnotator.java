package ai.deepcode.jbplugin.annotators;

import ai.deepcode.jbplugin.actions.DeepCodeIntentionAction;
import ai.deepcode.jbplugin.core.*;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DeepCodeAnnotator implements Annotator {

  @Override
  public void annotate(@NotNull PsiElement psiElement, @NotNull AnnotationHolder holder) {
    if (!(psiElement instanceof PsiFile)) return;
    PsiFile psiFile = (PsiFile) psiElement;
    if (!DeepCodeUtils.isSupportedFileFormat(psiFile)) return;
    final long annotatorId = System.currentTimeMillis();
    DCLogger.info(
        "Annotator ("
            + annotatorId
            + ") requested for file: "
            + psiFile.getName()
            + " with Holder: "
            + holder);

//    RunUtils.runInBackground(
//        psiElement.getProject(),
//        () -> {
                doAnnotate(psiFile, holder, annotatorId);
//            }, "DeepCodeAnnotator.annotate"
//    );
  }

  private static void doAnnotate(
      @NotNull PsiFile psiFile, @NotNull AnnotationHolder holder, long annotatorId) {
    DCLogger.info(
        "Annotator ("
            + annotatorId
            + ") started for file: "
            + psiFile.getName()
            + " with Holder: "
            + holder);
    AnalysisData.waitForUpdateAnalysisFinish();
    ProgressManager.checkCanceled();

    List<SuggestionForFile> suggestions = AnalysisData.getAnalysis(psiFile);
    DCLogger.info(
        "Annotator (" + annotatorId + ") suggestions gotten for file: " + psiFile.getName());
    ProgressManager.checkCanceled();

    for (SuggestionForFile suggestion : suggestions) {
      final String message = "DeepCode: " + suggestion.getMessage();
      Annotation annotation;
      for (TextRange range : suggestion.getRanges()) {
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
            new DeepCodeIntentionAction(psiFile, range, suggestion.getId(), false));
        annotation.registerFix(
            new DeepCodeIntentionAction(psiFile, range, suggestion.getId(), true));
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
