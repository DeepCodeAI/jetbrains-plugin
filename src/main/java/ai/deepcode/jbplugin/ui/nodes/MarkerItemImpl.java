package ai.deepcode.jbplugin.ui.nodes;

import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.search.TodoItemImpl;
import com.intellij.psi.search.TodoPattern;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

/** Marks Marker (for suggestion) item */
public class MarkerItemImpl extends TodoItemImpl {

  private final String message;

  public MarkerItemImpl(
          @NotNull PsiFile file, int startOffset, int endOffset, @NotNull TodoPattern pattern, String message) {
    super(file, startOffset, endOffset, pattern, Collections.emptyList());
    this.message = message;
  }

  public String getMessage() {
    return message;
  }
}
