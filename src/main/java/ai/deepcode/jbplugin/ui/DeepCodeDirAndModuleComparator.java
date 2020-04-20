// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0
// license that can be found in the LICENSE file.

package ai.deepcode.jbplugin.ui;

import ai.deepcode.jbplugin.ui.nodes.TodoFileNode;
import ai.deepcode.jbplugin.utils.DeepCodeUtils;
import com.intellij.ide.projectView.ProjectViewNode;
import ai.deepcode.jbplugin.ui.nodes.ModuleToDoNode;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.psi.PsiFile;

import java.util.Collections;
import java.util.Comparator;

public final class DeepCodeDirAndModuleComparator implements Comparator<NodeDescriptor> {
  public static final DeepCodeDirAndModuleComparator INSTANCE =
      new DeepCodeDirAndModuleComparator();

  private DeepCodeDirAndModuleComparator() {}

  @Override
  public int compare(NodeDescriptor obj1, NodeDescriptor obj2) {

    final int weight1 = obj1.getWeight();
    final int weight2 = obj2.getWeight();
    if (weight1 != weight2) return weight1 - weight2;

    if (obj1 instanceof TodoFileNode && obj2 instanceof TodoFileNode) {
      PsiFile file1 = ((TodoFileNode) obj1).getValue();
      PsiFile file2 = ((TodoFileNode) obj2).getValue();
      DeepCodeUtils.ErrorsWarningsInfos ewi1 = DeepCodeUtils.getEWI(Collections.singleton(file1));
      DeepCodeUtils.ErrorsWarningsInfos ewi2 = DeepCodeUtils.getEWI(Collections.singleton(file2));
      return (ewi1.getErrors() != ewi2.getErrors())
          ? ewi2.getErrors() - ewi1.getErrors()
          : (ewi1.getWarnings() != ewi2.getWarnings())
              ? ewi2.getWarnings() - ewi1.getWarnings()
              : ewi2.getInfos() - ewi1.getInfos();

    } else if (obj1 instanceof ProjectViewNode && obj2 instanceof ProjectViewNode) {
      return ((ProjectViewNode) obj1)
          .getTitle()
          .compareToIgnoreCase(((ProjectViewNode) obj2).getTitle());
    }
    if (obj1 instanceof ModuleToDoNode && obj2 instanceof ModuleToDoNode) {
      return ((ModuleToDoNode) obj1)
          .getValue()
          .getName()
          .compareToIgnoreCase(((ModuleToDoNode) obj2).getValue().getName());
    } else if (obj1 instanceof ModuleToDoNode) {
      return -1;
    } else if (obj2 instanceof ModuleToDoNode) {
      return 1;
    } else {
      throw new IllegalArgumentException(
          obj1.getClass().getName() + "," + obj2.getClass().getName());
    }
  }
}
