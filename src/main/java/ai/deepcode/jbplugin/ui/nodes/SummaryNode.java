// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0
// license that can be found in the LICENSE file.

package ai.deepcode.jbplugin.ui.nodes;

import ai.deepcode.jbplugin.core.AnalysisData;
import ai.deepcode.jbplugin.core.DCLogger;
import ai.deepcode.jbplugin.core.PDU;
import ai.deepcode.jbplugin.core.RunUtils;
import ai.deepcode.jbplugin.ui.DeepCodeDirAndModuleComparator;
import ai.deepcode.jbplugin.ui.HighlightedRegionProvider;
import ai.deepcode.jbplugin.ui.ToDoSummary;
import ai.deepcode.jbplugin.ui.TodoTreeBuilder;
import ai.deepcode.jbplugin.ui.utils.DeepCodeUIUtils;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.ui.HighlightedRegion;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class SummaryNode extends BaseToDoNode<ToDoSummary> implements HighlightedRegionProvider {
  private final List<HighlightedRegion> myHighlightedRegions;

  public SummaryNode(Project project, @NotNull ToDoSummary value, TodoTreeBuilder builder) {
    super(project, value, builder);
    myHighlightedRegions = ContainerUtil.createConcurrentList();
  }

  @Override
  @NotNull
  public Collection<AbstractTreeNode<?>> getChildren() {
    ArrayList<AbstractTreeNode<?>> children = new ArrayList<>();

    final ProjectFileIndex projectFileIndex =
        ProjectRootManager.getInstance(getProject()).getFileIndex();

    for (Iterator<PsiFile> i = myBuilder.getAllFiles(); i.hasNext(); ) {
      final PsiFile psiFile = i.next();
      if (psiFile == null) { // skip invalid PSI files
        continue;
      }
      TodoFileNode fileNode = new TodoFileNode(getProject(), psiFile, myBuilder, false);
      if (getTreeStructure().accept(psiFile) && !children.contains(fileNode)) {
        children.add(fileNode);
      }
    }

    /*    if (myToDoSettings.isModulesShown()) {

      for (Iterator i = myBuilder.getAllFiles(); i.hasNext();) {
        final PsiFile psiFile = (PsiFile)i.next();
        if (psiFile == null) { // skip invalid PSI files
          continue;
        }
        final VirtualFile virtualFile = psiFile.getVirtualFile();
        createModuleTodoNodeForFile(children, projectFileIndex, virtualFile);
      }
    }
    else {
      if (myToDoSettings.getIsPackagesShown()) {
        if (myBuilder instanceof CurrentFileTodosTreeBuilder){
          final Iterator<PsiFile> allFiles = myBuilder.getAllFiles();
          if(allFiles.hasNext()){
            children.add(new TodoFileNode(myProject, allFiles.next(), myBuilder, false));
          }
        } else {
          TodoTreeHelper.getInstance(getProject()).addPackagesToChildren(children, null, myBuilder);
        }
      }
      else {

        for (Iterator i = myBuilder.getAllFiles(); i.hasNext();) {
          final PsiFile psiFile = (PsiFile)i.next();
          if (psiFile == null) { // skip invalid PSI files
            continue;
          }
          TodoFileNode fileNode = new TodoFileNode(getProject(), psiFile, myBuilder, false);
          if (getTreeStructure().accept(psiFile) && !children.contains(fileNode)) {
            children.add(fileNode);
          }
        }
      }
    }*/
    children.sort(DeepCodeDirAndModuleComparator.INSTANCE);
    return children;
  }

  protected void createModuleTodoNodeForFile(
      ArrayList<? super AbstractTreeNode<?>> children,
      ProjectFileIndex projectFileIndex,
      VirtualFile virtualFile) {
    Module module = projectFileIndex.getModuleForFile(virtualFile);
    if (module != null) {
      ModuleToDoNode moduleToDoNode = new ModuleToDoNode(getProject(), module, myBuilder);
      if (!children.contains(moduleToDoNode)) {
        children.add(moduleToDoNode);
      }
    }
  }

  @Override
  public void update(@NotNull PresentationData presentation) {
    String message;
    myHighlightedRegions.clear();
    final Project project = getProject();
    if (project == null) {
      DCLogger.warn("Project is NULL");
      return;
    }
    if (AnalysisData.getInstance().isAnalysisResultsNOTAvailable(project)) {
      message = "Analysis results are not available yet...";
    } else {
      int todoItemCount = getTodoItemCount(getValue());
      int fileCount = getFileCount(getValue());
      message =
          IdeBundle.message("node.todo.summary", todoItemCount, fileCount)
              .replace("TODO item", "occurrence");
      DCLogger.info(message);
      message =
          DeepCodeUIUtils.addErrWarnInfoCounts(
              // fixme
              PDU.toPsiFiles(AnalysisData.getInstance().getAllFilesWithSuggestions(project)),
              message,
              false,
              myHighlightedRegions);
    }
    presentation.setPresentableText(message);
    myBuilder.expandTree(2);
  }

  /*
    @Override
    public String getTestPresentation() {
      return "Summary";
    }
  */

  @Nullable
  @Override
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    return "Summary";
  }

  @Override
  public int getFileCount(ToDoSummary summary) {
    int count = 0;
    for (Iterator<PsiFile> i = myBuilder.getAllFiles(); i.hasNext(); ) {
      PsiFile psiFile = i.next();
      if (psiFile == null) { // skip invalid PSI files
        continue;
      }
      //      if (getTreeStructure().accept(psiFile)) {
      count++;
    }
    return count;
  }

  @Override
  public int getTodoItemCount(final ToDoSummary val) {
    int count = 0;
    for (final Iterator<PsiFile> i = myBuilder.getAllFiles(); i.hasNext(); ) {
      final PsiFile psiFile = i.next();
      count +=
          RunUtils.computeInReadActionInSmartMode(
              psiFile.getProject(), () -> getTreeStructure().getTodoItemCount(psiFile));
    }
    return count;
  }

  @Override
  public int getWeight() {
    return 0;
  }

  @Override
  public Iterable<HighlightedRegion> getHighlightedRegions() {
    return myHighlightedRegions;
  }
}
