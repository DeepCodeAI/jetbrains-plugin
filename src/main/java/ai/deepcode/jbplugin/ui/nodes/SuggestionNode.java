package ai.deepcode.jbplugin.ui.nodes;

import ai.deepcode.jbplugin.ui.SmartTodoItemPointer;
import ai.deepcode.jbplugin.ui.SmartTodoItemPointerComparator;
import ai.deepcode.jbplugin.ui.TodoTreeBuilder;
import ai.deepcode.jbplugin.utils.AnalysisData;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.ide.todo.TodoFilter;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.search.TodoItemImpl;
import com.intellij.psi.search.PsiTodoSearchHelper;
import com.intellij.psi.search.TodoItem;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class SuggestionNode extends BaseToDoNode<PsiFile> {
  private final AnalysisData.SuggestionForFile suggestion;

  protected SuggestionNode(
      Project project,
      @NotNull PsiFile psiFile,
      TodoTreeBuilder builder,
      @NotNull AnalysisData.SuggestionForFile suggestion) {
    super(project, psiFile, builder);
    this.suggestion = suggestion;
  }

  @Override
  public int getFileCount(PsiFile val) {
    return 1;
  }

  @Override
  public int getTodoItemCount(PsiFile val) {
    return suggestion.getRanges().size();
  }

  @NotNull
  @Override
  public Collection<? extends AbstractTreeNode<?>> getChildren() {
    try {
      return createGeneralList();
    }
    catch (IndexNotReadyException e) {
      return Collections.emptyList();
    }
  }

  private TodoItem[] findAllTodos(final PsiFile psiFile, final PsiTodoSearchHelper helper) {
    final List<TodoItem> todoItems = new ArrayList<>();
    for (TextRange range : suggestion.getRanges()) {
      todoItems.add(
          new TodoItemImpl(
              psiFile,
              range.getStartOffset(),
              range.getEndOffset(),
              TodoConfiguration.getInstance().getTodoPatterns()[0],
              Collections.emptyList()));
    }
    /*    final List<TodoItem> todoItems = new ArrayList<>(Arrays.asList(helper.findTodoItems(psiFile)));

    psiFile.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        if (element instanceof PsiLanguageInjectionHost) {
          InjectedLanguageManager.getInstance(psiFile.getProject()).enumerate(element, (injectedPsi, places) -> {
            if (places.size() == 1) {
              Document document = PsiDocumentManager.getInstance(injectedPsi.getProject()).getCachedDocument(injectedPsi);
              if (!(document instanceof DocumentWindow)) return;
              for (TodoItem item : helper.findTodoItems(injectedPsi)) {
                TextRange rangeInHost = ((DocumentWindow)document).injectedToHost(item.getTextRange());
                List<TextRange> additionalRanges = ContainerUtil.map(item.getAdditionalTextRanges(),
                                                                     ((DocumentWindow)document)::injectedToHost);
                TodoItemImpl hostItem = new TodoItemImpl(psiFile, rangeInHost.getStartOffset(), rangeInHost.getEndOffset(),
                                                         item.getPattern(), additionalRanges);
                todoItems.add(hostItem);
              }
            }
          });
        }
        super.visitElement(element);
      }
    });*/
    return todoItems.toArray(new TodoItem[0]);
  }

  private Collection<? extends AbstractTreeNode<?>> createGeneralList() {
    PsiFile psiFile = getValue();
    final TodoItem[] items =
        findAllTodos(psiFile, myBuilder.getTodoTreeStructure().getSearchHelper());
    List<TodoItemNode> children = new ArrayList<>(items.length);
    final Document document = PsiDocumentManager.getInstance(getProject()).getDocument(psiFile);
    if (document != null) {
      for (final TodoItem todoItem : items) {
        if (todoItem.getTextRange().getEndOffset() < document.getTextLength() + 1) {
          final SmartTodoItemPointer pointer = new SmartTodoItemPointer(todoItem, document);
          TodoFilter todoFilter = getToDoFilter();
          if (todoFilter != null) {
            if (todoFilter.contains(todoItem.getPattern())) {
              children.add(new TodoItemNode(getProject(), pointer, myBuilder));
            }
          } else {
            children.add(new TodoItemNode(getProject(), pointer, myBuilder));
          }
        }
      }
    }
    children.sort(SmartTodoItemPointerComparator.ourInstance);
    return children;
  }

  private TodoFilter getToDoFilter() {
    return null;
    // myBuilder.getTodoTreeStructure().getTodoFilter();
  }

  @Override
  protected void update(@NotNull PresentationData presentation) {
    presentation.setPresentableText(suggestion.getMessage());
  }
}
