package ai.deepcode.jbplugin.annotators;

import ai.deepcode.jbplugin.actions.DeepCodeIntentionAction;
import ai.deepcode.jbplugin.ui.myTodoView;
import ai.deepcode.jbplugin.utils.AnalysisData;
import ai.deepcode.jbplugin.utils.DeepCodeUtils;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
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
    if (!DeepCodeUtils.isSupportedFileFormat(psiFile)) return Collections.emptyList();
    return ProgressManager.getInstance().runProcess(
            () -> AnalysisData.getAnalysis(psiFile),
               new BackgroundableProcessIndicator(
                      psiFile.getProject(),
                      "DeepCode: Analysing " + psiFile.getName() + " file... ",
                      new PerformInBackgroundOption() {
                        @Override
                        public boolean shouldStartInBackground() {
                          return true;
                        }
                      },
                      "Stop",
                      "Stop file analysis",
                      true)
            );
  }

  @SuppressWarnings("deprecation") // later move to .newAnnotation introduced in 2020.1
  @Override
  public void apply(
      @NotNull PsiFile psiFile,
      List<AnalysisData.SuggestionForFile> suggestions,
      @NotNull AnnotationHolder holder) {
    if (suggestions == null) return;
    for (AnalysisData.SuggestionForFile suggestion : suggestions) {
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
        annotation.registerFix(new DeepCodeIntentionAction(psiFile, range, suggestion.getId(), false));
        annotation.registerFix(new DeepCodeIntentionAction(psiFile, range, suggestion.getId(), true));
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
