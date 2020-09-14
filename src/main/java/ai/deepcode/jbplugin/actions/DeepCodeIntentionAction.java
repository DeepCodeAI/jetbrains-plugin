package ai.deepcode.jbplugin.actions;

import ai.deepcode.jbplugin.core.AnalysisData;
import ai.deepcode.jbplugin.core.PDU;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.Commenter;
import com.intellij.lang.LanguageCommenters;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

public class DeepCodeIntentionAction implements IntentionAction {
  private final PsiFile myPsiFile;
  private final TextRange myRange;
  private final String rule;
  private final boolean isFileIntention;

  public DeepCodeIntentionAction(
      PsiFile psiFile, TextRange range, String rule, boolean isFileIntention) {
    myPsiFile = psiFile;
    myRange = range;
    this.rule = rule;
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
    return editor.getEditorKind() != EditorKind.PREVIEW
        // otherwise preview editor will close (no suggestion found) before description entered.
        && file.equals(myPsiFile)
        && AnalysisData.getInstance().isFileInCache(file)
        && AnalysisData.getInstance().getAnalysis(file).stream()
            .flatMap(s -> s.getRanges().stream())
            .map(PDU::toTextRange)
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
    if (editor == null || file == null) return;
    Document document = editor.getDocument();
    if (document.getTextLength() < 0) return;
    int lineNumber = document.getLineNumber(myRange.getStartOffset());
    int insertPosition = document.getLineStartOffset(lineNumber);

    final int lineStart = document.getLineStartOffset(lineNumber);
    final int lineEnd = document.getLineEndOffset(lineNumber);
    String lineText = document.getText(new TextRange(lineStart, lineEnd));

    String prefix = getLeadingSpaces(lineText) + getLineCommentPrefix(file);
    String postfix = "\n";
    if (lineNumber > 0) {

      final int prevLineStart = document.getLineStartOffset(lineNumber - 1);
      final int prevLineEnd = document.getLineEndOffset(lineNumber - 1);
      String prevLine = document.getText(new TextRange(prevLineStart, prevLineEnd)).toLowerCase();

      final Pattern ignorePattern =
          Pattern.compile(".*" + getLineCommentPrefix(file) + ".*deepcode\\s?ignore.*");
      if (ignorePattern.matcher(prevLine).matches()) {
        prefix = ",";
        postfix = "";
        insertPosition -= 1;
      }
    }

//    final String[] splitedId = rule.split("%2F");
//    String suggestionId = splitedId[splitedId.length - 1];

    final String ignoreCommand =
        prefix
            + (prefix.endsWith(" ") ? "" : " ")
            + (isFileIntention ? "file " : "")
            + "deepcode ignore "
            + rule
            + ": ";
    final String ignoreDescription = "<please specify a reason of ignoring this>";

    document.insertString(insertPosition, ignoreCommand + ignoreDescription + postfix);

    int caretOffset = insertPosition + ignoreCommand.length();
    editor.getCaretModel().moveToOffset(caretOffset);

    editor.getSelectionModel().setSelection(caretOffset, caretOffset + ignoreDescription.length());

    // set focus on editor if called from DeepCode Panel
    IdeFocusManager.getInstance(project).requestFocus(editor.getContentComponent(), true);
  }

  private static final String DEFAULT_LINE_COMMENT_PREFIX = "//";

  @NotNull
  private static String getLineCommentPrefix(@NotNull PsiFile psiFile) {
    final Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(psiFile.getLanguage());
    if (commenter == null) return DEFAULT_LINE_COMMENT_PREFIX;
    String prefix = commenter.getLineCommentPrefix();
    return prefix == null ? DEFAULT_LINE_COMMENT_PREFIX : prefix;
  }

  private static String getLeadingSpaces(@NotNull String lineText) {
    int index = 0;
    while (index < lineText.length() && Character.isWhitespace(lineText.charAt(index))) index++;
    return lineText.substring(0, index);
  }

  /**
   * Indicate whether this action should be invoked inside write action. Should return {@code false}
   * if, e.g., a modal dialog is shown inside the action. If false is returned the action itself is
   * responsible for starting write action when needed, by calling
   *
   * @return {@code true} if the intention requires a write action, {@code false} otherwise.
   */
  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
