package ai.deepcode.jbplugin.ui.nodes;

import ai.deepcode.javaclient.core.MyTextRange;
import ai.deepcode.jbplugin.ui.HighlightedRegionProvider;
import ai.deepcode.jbplugin.ui.SmartTodoItemPointer;
import ai.deepcode.jbplugin.ui.TodoTreeBuilder;
import ai.deepcode.javaclient.core.SuggestionForFile;
import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.ide.todo.TodoFilter;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.search.TodoItemImpl;
import com.intellij.psi.search.PsiTodoSearchHelper;
import com.intellij.psi.search.TodoItem;
import com.intellij.ui.HighlightedRegion;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class SuggestionNode extends BaseToDoNode<SuggestionForFile>
    implements HighlightedRegionProvider {
  @NotNull private final PsiFile myPsiFile;
  @Nullable private final Document document;
  @NotNull private final Project project;
  private final List<HighlightedRegion> myHighlightedRegions;
  private final SuggestionForFile suggestion;

  protected SuggestionNode(
      Project project,
      @NotNull PsiFile psiFile,
      TodoTreeBuilder builder,
      @NotNull SuggestionForFile suggestion) {
    super(project, suggestion, builder);
    myPsiFile = psiFile;
    this.project = (getProject() != null) ? getProject() : psiFile.getProject();
    document = PsiDocumentManager.getInstance(project).getDocument(myPsiFile);
    this.suggestion = getValue();
    myHighlightedRegions = ContainerUtil.createConcurrentList();
  }

  @NotNull
  @Override
  public Collection<? extends AbstractTreeNode<?>> getChildren() {
    try {
      return createGeneralList();
    } catch (IndexNotReadyException e) {
      return Collections.emptyList();
    }
  }

  private TodoItem[] findAllTodos(final PsiFile psiFile, final PsiTodoSearchHelper helper) {
    final List<TodoItem> todoItems = new ArrayList<>();
    for (MyTextRange range : suggestion.getRanges()) {
      List<TextRange> additionalRanges =
          range.getMarkers().values().stream()
              .flatMap(Collection::stream)
              .map(it -> new TextRange(it.getStart(), it.getEnd()))
              .collect(Collectors.toList());
      todoItems.add(
          new TodoItemImpl(
              psiFile,
              range.getStart(),
              range.getEnd(),
              TodoConfiguration.getInstance().getTodoPatterns()[0],
              additionalRanges
              // Collections.emptyList()
              ));

      for (Map.Entry<MyTextRange, List<MyTextRange>> entry : range.getMarkers().entrySet()) {
        final String markerMsg =
            suggestion.getMessage().substring(entry.getKey().getStart(), entry.getKey().getEnd());
        for (MyTextRange markerRange : entry.getValue()) {
          todoItems.add(
              new MarkerItemImpl(
                  psiFile,
                  markerRange.getStart(),
                  markerRange.getEnd(),
                  TodoConfiguration.getInstance().getTodoPatterns()[0],
                  markerMsg));
        }
      }
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
    final TodoItem[] items =
        findAllTodos(myPsiFile, myBuilder.getTodoTreeStructure().getSearchHelper());
    List<TodoItemNode> children = new ArrayList<>(items.length);
    if (document != null) {
      for (final TodoItem todoItem : items) {
        if (todoItem.getTextRange().getEndOffset() < document.getTextLength() + 1) {
          final SmartTodoItemPointer pointer = new SmartTodoItemPointer(todoItem, document);
          TodoFilter todoFilter = getToDoFilter();
          if (todoFilter != null) {
            if (todoFilter.contains(todoItem.getPattern())) {
              children.add(new TodoItemNode(project, pointer, myBuilder));
            }
          } else {
            children.add(new TodoItemNode(project, pointer, myBuilder));
          }
        }
      }
    }
    // children.sort(SmartTodoItemPointerComparator.ourInstance);
    return children;
  }

  private TodoFilter getToDoFilter() {
    return null;
    // myBuilder.getTodoTreeStructure().getTodoFilter();
  }

  @Override
  protected void update(@NotNull PresentationData presentation) {
    presentation.setPresentableText(suggestion.getMessage());
    Icon severityIcon;
    if (suggestion.getSeverity() == 1) severityIcon = AllIcons.General.Information;
    else if (suggestion.getSeverity() == 2) severityIcon = AllIcons.General.Warning;
    else if (suggestion.getSeverity() == 3) severityIcon = AllIcons.General.Error;
    else severityIcon = AllIcons.General.ShowWarning;
    presentation.setIcon(severityIcon);
    // highlight markers
    myHighlightedRegions.clear();
    TextAttributes markerTextAttributes =
        EditorColorsManager.getInstance()
            .getGlobalScheme()
            .getAttributes(CodeInsightColors.WEAK_WARNING_ATTRIBUTES)
            .clone();
    for (MyTextRange range : suggestion.getRanges()) {
      for (MyTextRange myRange : range.getMarkers().keySet()) {
        myHighlightedRegions.add(
            new HighlightedRegion(myRange.getStart(), myRange.getEnd(), markerTextAttributes));
      }
    }
  }

  @Override
  public int getFileCount(SuggestionForFile val) {
    return 1;
  }

  @Override
  public int getTodoItemCount(SuggestionForFile val) {
    return val.getRanges().size();
  }

  @Override
  public Iterable<HighlightedRegion> getHighlightedRegions() {
    return myHighlightedRegions;
  }
}
