package ai.deepcode.jbplugin.actions;

import ai.deepcode.jbplugin.utils.AnalysisData;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class DeepCodeIntentionAction implements IntentionAction {
  private final PsiFile myPsiFile;
  private final TextRange myRange;
  private final String fullSuggestionId;
  private final boolean isFileIntention;

  public DeepCodeIntentionAction(
      PsiFile psiFile, TextRange range, String id, boolean isFileIntention) {
    myPsiFile = psiFile;
    myRange = range;
    fullSuggestionId = id;
    this.isFileIntention = isFileIntention;
  }

  /**
   * Returns text to be shown in the list of available actions, if this action is available.
   *
   * @return the text to show in the intention popup.
   * @see #isAvailable(Project, Editor, PsiFile)
   */
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getText() {
    return isFileIntention
        ? "Ignore this suggestion in current file (DeepCode)"
        : "Ignore this particular suggestion (DeepCode)";
  }

  /**
   * Returns the name of the family of intentions. It is used to externalize "auto-show" state of
   * intentions. When the user clicks on a light bulb in intention list, all intentions with the
   * same family name get enabled/disabled. The name is also shown in settings tree.
   *
   * @return the intention family name.
   * @see IntentionManager#registerIntentionAndMetaData(IntentionAction, String...)
   */
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return "DeepCodeIntentionAction";
  }

  /**
   * Checks whether this intention is available at a caret offset in the file. If this method
   * returns true, a light bulb for this intention is shown.
   *
   * @param project the project in which the availability is checked.
   * @param editor the editor in which the intention will be invoked.
   * @param file the file open in the editor.
   * @return {@code true} if the intention is available, {@code false} otherwise.
   */
  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return file.equals(myPsiFile)
        && AnalysisData.getAnalysis(file).stream()
            .flatMap(s -> s.getRanges().stream())
            .anyMatch(r -> r.contains(myRange));
  }

  /**
   * Called when user invokes intention. This method is called inside command. If {@link
   * #startInWriteAction()} returns {@code true}, this method is also called inside write action.
   *
   * @param project the project in which the intention is invoked.
   * @param editor the editor in which the intention is invoked.
   * @param file the file open in the editor.
   */
  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file)
      throws IncorrectOperationException {
    Document document = editor.getDocument();
    if (document.getTextLength() < 0) return;
    int lineNumber = document.getLineNumber(myRange.getStartOffset());
    int lineStartOffset = document.getLineStartOffset(lineNumber);
    final String[] splitedId = fullSuggestionId.split("%2F");
    String suggestionId = splitedId[splitedId.length - 1];
    document.insertString(
        lineStartOffset,
        "// "
            + (isFileIntention ? "file " : "")
            + "deepcode ignore "
            + suggestionId
            + ": <please specify a reason of ignoring this>\n");
  }

  /**
   * Indicate whether this action should be invoked inside write action. Should return {@code false}
   * if, e.g., a modal dialog is shown inside the action. If false is returned the action itself is
   * responsible for starting write action when needed, by calling {@link
   * Application#runWriteAction(Runnable)}.
   *
   * @return {@code true} if the intention requires a write action, {@code false} otherwise.
   */
  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
