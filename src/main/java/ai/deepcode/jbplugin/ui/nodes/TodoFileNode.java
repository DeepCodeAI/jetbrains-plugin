// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package ai.deepcode.jbplugin.ui.nodes;

import ai.deepcode.jbplugin.utils.AnalysisData;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import ai.deepcode.jbplugin.ui.SmartTodoItemPointer;
import ai.deepcode.jbplugin.ui.SmartTodoItemPointerComparator;
import ai.deepcode.jbplugin.ui.TodoTreeBuilder;
import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.ide.todo.TodoFilter;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.search.TodoItemImpl;
import com.intellij.psi.search.PsiTodoSearchHelper;
import com.intellij.psi.search.TodoItem;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public final class TodoFileNode extends PsiFileNode {
  private final TodoTreeBuilder myBuilder;
  private final boolean mySingleFileMode;

  public TodoFileNode(Project project,
                      @NotNull PsiFile file,
                      TodoTreeBuilder treeBuilder,
                      boolean singleFileMode){
    super(project,file,ViewSettings.DEFAULT);
    myBuilder=treeBuilder;
    mySingleFileMode=singleFileMode;
  }

  @Override
  public Collection<AbstractTreeNode<?>> getChildrenImpl() {
    try {
      if (!mySingleFileMode) {
        return (Collection<AbstractTreeNode<?>>)createGeneralList();
      }
      return (Collection<AbstractTreeNode<?>>)createListForSingleFile();
    }
    catch (IndexNotReadyException e) {
      return Collections.emptyList();
    }
  }

  // fixme: same as createGeneralList
  private Collection<? extends AbstractTreeNode<?>> createListForSingleFile() {
    return createGeneralList();
/*
    PsiFile psiFile = getValue();
    TodoItem[] items= findAllTodos(psiFile, myBuilder.getTodoTreeStructure().getSearchHelper());
    List<TodoItemNode> children= new ArrayList<>(items.length);
    Document document = PsiDocumentManager.getInstance(getProject()).getDocument(psiFile);
    if (document != null) {
      for (TodoItem todoItem : items) {
        if (todoItem.getTextRange().getEndOffset() < document.getTextLength() + 1) {
          SmartTodoItemPointer pointer = new SmartTodoItemPointer(todoItem, document);
          TodoFilter toDoFilter = getToDoFilter();
          if (toDoFilter != null) {
            TodoItemNode itemNode = new TodoItemNode(getProject(), pointer, myBuilder);
            if (toDoFilter.contains(todoItem.getPattern())) {
              children.add(itemNode);
            }
          } else {
            children.add(new TodoItemNode(getProject(), pointer, myBuilder));
          }
        }
      }
    }
    children.sort(SmartTodoItemPointerComparator.ourInstance);
    return children;
*/
  }

  private Collection<? extends AbstractTreeNode<?>> createGeneralList() {
    PsiFile psiFile = getValue();
    return AnalysisData.getAnalysis(psiFile)
            .stream()
            .map(suggestion -> new SuggestionNode(getProject(), psiFile, myBuilder, suggestion))
            .collect(Collectors.toList());
  }

  @Override
  protected void updateImpl(@NotNull PresentationData data) {
    super.updateImpl(data);
    String newName;
    if(myBuilder.getTodoTreeStructure().isPackagesShown()){
      newName=getValue().getName();
    }else{
      newName=mySingleFileMode ? getValue().getName() : getValue().getVirtualFile().getPresentableUrl();
    }

    data.setPresentableText(newName);
    int todoItemCount;
    try {
      todoItemCount = myBuilder.getTodoTreeStructure().getTodoItemCount(getValue());
    }
    catch (IndexNotReadyException e) {
      return;
    }
    if (todoItemCount > 0) {
      data.setLocationString(IdeBundle.message("node.todo.items", todoItemCount));
    }
  }

  @Override
  public int getWeight() {
    return 4;
  }
}
