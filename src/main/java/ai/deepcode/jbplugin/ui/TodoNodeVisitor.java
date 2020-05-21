// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package ai.deepcode.jbplugin.ui;

import com.intellij.ide.projectView.ProjectViewNode;
import ai.deepcode.jbplugin.ui.nodes.BaseToDoNode;
import ai.deepcode.jbplugin.ui.nodes.SummaryNode;
import ai.deepcode.jbplugin.ui.nodes.ToDoRootNode;
import ai.deepcode.jbplugin.ui.nodes.TodoTreeHelper;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.tree.AbstractTreeNodeVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;


class TodoNodeVisitor extends AbstractTreeNodeVisitor<Object> {
  private final VirtualFile myFile;

  TodoNodeVisitor(@NotNull Supplier<Object> supplier, VirtualFile file) {
    super(supplier, null);
    myFile = file;
  }

  @Override
  protected boolean contains(@NotNull AbstractTreeNode node, @NotNull Object element) {
    if (node instanceof SummaryNode || node instanceof ToDoRootNode) return true;
    if (node instanceof ProjectViewNode) {
      if (myFile == null) {
        return TodoTreeHelper.getInstance(node.getProject()).contains((ProjectViewNode)node, element);
      }
    }
    return node instanceof BaseToDoNode && ((BaseToDoNode)node).contains(element) ||
           node instanceof ProjectViewNode && ((ProjectViewNode)node).contains(myFile);
  }
}
