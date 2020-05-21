/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.deepcode.jbplugin.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;

import java.util.*;

/**
 * @author Vladimir Kondratyev
 */
final class FileTree {
  private static final Logger LOG = Logger.getInstance(FileTree.class);

  private final Map<VirtualFile, List<VirtualFile>> myDirectory2Children;
  private final Set<VirtualFile> myFiles;
  private final Map<VirtualFile, List<VirtualFile>> myStrictDirectory2Children;

  FileTree() {
    myDirectory2Children = ContainerUtil.newConcurrentMap();
    myFiles = ContainerUtil.newConcurrentSet();
    myStrictDirectory2Children = ContainerUtil.newConcurrentMap();
  }

  void add(VirtualFile file) {
    if (myFiles.contains(file)) {
      return;
    }

    VirtualFile dir = file.getParent();
    if (dir == null) {
      LOG.error(file);
      return;
    }

    myFiles.add(file);

    List<VirtualFile> children = myStrictDirectory2Children
            .computeIfAbsent(dir, d -> ContainerUtil.createConcurrentList());
    LOG.assertTrue(!children.contains(file));
    children.add(file);

    children = myDirectory2Children
            .computeIfAbsent(dir, d -> ContainerUtil.createConcurrentList());
    LOG.assertTrue(!children.contains(file));
    children.add(file);
    if (children.size() > 1) return;

    VirtualFile parent = dir.getParent();
    while (parent != null) {
      children = myDirectory2Children
              .computeIfAbsent(parent, d -> ContainerUtil.createConcurrentList());
      if (!children.contains(dir)) children.add(dir);
      if (children.size() > 1) return;
      dir = parent;
      parent = parent.getParent();
    }
  }

  boolean isDirectoryEmpty(VirtualFile dir) {
    final List<VirtualFile> files = myStrictDirectory2Children.get(dir);
    return files == null || files.isEmpty();
  }

  List<VirtualFile> getFilesUnderDirectory(VirtualFile dir) {
    List<VirtualFile> filesList = new ArrayList<>();
    List<VirtualFile> files = myStrictDirectory2Children.get(dir);
    if (files != null) {
      filesList.addAll(files);
    }
    return filesList;
  }

  void removeFile(VirtualFile file) {
    if (!myFiles.contains(file)) {
      return;
    }

    myFiles.remove(file);
    List<VirtualFile> dirsToBeRemoved = null;
    for (Map.Entry<VirtualFile, List<VirtualFile>> entry : myDirectory2Children.entrySet()) {
      VirtualFile _directory = entry.getKey();
      List<VirtualFile> children = entry.getValue();
      LOG.assertTrue(children != null);
      dirsToBeRemoved = collectDirsToRemove(file, children, dirsToBeRemoved, _directory);
    }
    myStrictDirectory2Children.values().forEach(files -> files.remove(file));
    // We have remove also all removed (empty) directories
    if (dirsToBeRemoved != null) {
      LOG.assertTrue(dirsToBeRemoved.size() > 0);
      for (VirtualFile dirToBeRemoved : dirsToBeRemoved) {
        removeDir(dirToBeRemoved);
      }
    }
  }

  /**
   * The method removes specified {@code psiDirectory} from the tree. The directory should be empty,
   * otherwise the method shows java.lang.IllegalArgumentException
   */
  private void removeDir(VirtualFile psiDirectory) {
    if (!myDirectory2Children.containsKey(psiDirectory)) {
      throw new IllegalArgumentException("directory is not in the tree: " + psiDirectory);
    }
    List<VirtualFile> children = myDirectory2Children.remove(psiDirectory);
    if (children == null) {
      throw new IllegalArgumentException("directory has no children list: " + psiDirectory);
    }
    if (children.size() > 0) {
      throw new IllegalArgumentException("directory isn't empty: " + psiDirectory);
    }
    //
    myStrictDirectory2Children.remove(psiDirectory);
    List<VirtualFile> dirsToBeRemoved = null;
    for (Map.Entry<VirtualFile, List<VirtualFile>> entry : myDirectory2Children.entrySet()) {
      VirtualFile _directory = entry.getKey();
      List<VirtualFile> _children = entry.getValue();
      LOG.assertTrue(_children != null);
      dirsToBeRemoved = collectDirsToRemove(psiDirectory, _children, dirsToBeRemoved, _directory);
    }
    //
    if (dirsToBeRemoved != null) {
      for (VirtualFile dirToBeRemoved : dirsToBeRemoved) {
        removeDir(dirToBeRemoved);
      }
    }
  }

  private static List<VirtualFile> collectDirsToRemove(VirtualFile psiDirectory,
                                                       List<VirtualFile> children,
                                                       List<VirtualFile> dirsToBeRemoved,
                                                       VirtualFile _directory) {
    if (children.contains(psiDirectory)) {
      children.remove(psiDirectory);
      if (children.size() == 0) {
        if (dirsToBeRemoved == null) {
          dirsToBeRemoved = new ArrayList<>(2);
        }
        dirsToBeRemoved.add(_directory); // we have remove empty _directory
      }
    }
    return dirsToBeRemoved;
  }

  boolean contains(VirtualFile file) {
    return myFiles.contains(file);
  }

  void clear() {
    myStrictDirectory2Children.clear();
    myDirectory2Children.clear();
    myFiles.clear();
  }

  /**
   * @return iterator of all files.
   */
  Iterator<VirtualFile> getFileIterator() {
    return myFiles.iterator();
  }

  /**
   * @return all files (in depth) located under specified {@code psiDirectory}.
   *         Please note that returned files can be invalid.
   */
  List<VirtualFile> getFiles(VirtualFile dir) {
    List<VirtualFile> filesList = new ArrayList<>();
    collectFiles(dir, filesList);
    return filesList;
  }

  private void collectFiles(VirtualFile dir, List<? super VirtualFile> filesList) {
    List<VirtualFile> children = myDirectory2Children.get(dir);
    if (children != null) {
      for (VirtualFile child : children) {
        ProgressManager.checkCanceled();
        if (!child.isDirectory()) {
          if (LOG.isDebugEnabled()) {
            LOG.assertTrue(!filesList.contains(child));
          }
          filesList.add(child);
        }
        else {
          collectFiles(child, filesList);
        }
      }
    }
  }
}
