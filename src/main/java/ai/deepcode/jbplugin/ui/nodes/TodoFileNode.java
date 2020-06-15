// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0
// license that can be found in the LICENSE file.

package ai.deepcode.jbplugin.ui.nodes;

import ai.deepcode.jbplugin.ui.HighlightedRegionProvider;
import ai.deepcode.jbplugin.ui.TodoTreeBuilder;
import ai.deepcode.jbplugin.ui.utils.DeepCodeUIUtils;
import ai.deepcode.jbplugin.core.AnalysisData;
import ai.deepcode.jbplugin.core.DeepCodeUtils;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.ui.HighlightedRegion;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class TodoFileNode extends PsiFileNode implements HighlightedRegionProvider {
  private final List<HighlightedRegion> myHighlightedRegions;

  private final TodoTreeBuilder myBuilder;
  private final boolean mySingleFileMode;

  public TodoFileNode(
      Project project, @NotNull PsiFile file, TodoTreeBuilder treeBuilder, boolean singleFileMode) {
    super(project, file, ViewSettings.DEFAULT);
    myBuilder = treeBuilder;
    mySingleFileMode = singleFileMode;
    myHighlightedRegions = ContainerUtil.createConcurrentList();
  }

  @Override
  public Collection<AbstractTreeNode> getChildrenImpl() {
    try {
      if (!mySingleFileMode) {
        return createGeneralList();
      }
      return createListForSingleFile();
    } catch (IndexNotReadyException e) {
      return Collections.emptyList();
    }
  }

  // fixme: same as createGeneralList
  private Collection<AbstractTreeNode> createListForSingleFile() {
    return createGeneralList();
  }

  private Collection<AbstractTreeNode> createGeneralList() {
    PsiFile psiFile = getValue();
    return AnalysisData.getInstance().getAnalysis(psiFile).stream()
        .map(suggestion -> new SuggestionNode(getProject(), psiFile, myBuilder, suggestion))
        .sorted((o1, o2) -> o2.getValue().getSeverity() - o1.getValue().getSeverity())
        .collect(Collectors.toList());
  }

  @Override
  protected void updateImpl(@NotNull PresentationData data) {
    super.updateImpl(data);
    PsiFile psiFile = getValue();
    String newName = DeepCodeUtils.getDeepCodedFilePath(psiFile);
    final int length = newName.length();
    if (length > 100) {
      newName = "..." + newName.substring(length - 97, length);
    }
/*
    if (myBuilder.getTodoTreeStructure().isPackagesShown()) {
      newName = getValue().getName();
    } else {
      newName =
          mySingleFileMode ? getValue().getName() : getValue().getVirtualFile().getPresentableUrl();
    }
*/
    String message =
        DeepCodeUIUtils.addErrWarnInfoCounts(
            Collections.singleton(psiFile), newName, false, myHighlightedRegions);
    data.setPresentableText(message);

    //todo remove?, not shown
    int todoItemCount;
    try {
      todoItemCount = myBuilder.getTodoTreeStructure().getTodoItemCount(getValue());
    } catch (IndexNotReadyException e) {
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

  @Override
  public Iterable<HighlightedRegion> getHighlightedRegions() {
    return myHighlightedRegions;
  }
}
