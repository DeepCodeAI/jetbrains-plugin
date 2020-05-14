package ai.deepcode.jbplugin.annotators;

import ai.deepcode.jbplugin.actions.DeepCodeIntentionAction;
import ai.deepcode.jbplugin.core.AnalysisData;
import ai.deepcode.jbplugin.core.DCLogger;
import ai.deepcode.jbplugin.core.DeepCodeUtils;
import ai.deepcode.jbplugin.core.SuggestionForFile;
import ai.deepcode.jbplugin.ui.myTodoView;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class DeepCodeAnnotator implements Annotator {

  @Override
  public void annotate(@NotNull PsiElement psiElement, @NotNull AnnotationHolder holder) {
    if (!(psiElement instanceof PsiFile)) return;
    PsiFile psiFile = (PsiFile) psiElement;
    DCLogger.info("Annotator started for file: " + psiFile);
    if (!DeepCodeUtils.isSupportedFileFormat(psiFile)) return;

    List<SuggestionForFile> suggestions = AnalysisData.getAnalysis(psiFile);
    DCLogger.info("Annotator: suggestions gotten for file: " + psiFile);

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
    ServiceManager.getService(psiFile.getProject(), myTodoView.class).refresh();
  }
}
