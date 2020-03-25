package ai.deepcode.jbplugin;

import com.intellij.execution.impl.ConsoleViewUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.changes.ui.CurrentBranchComponent;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.AbstractLayoutManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class DeepCodeToolWindowFactory implements ToolWindowFactory, Disposable {

  public static class CurrentFileEditor {

    private static EditorEx myEditor;

    public static EditorEx get(@NotNull Project project) {
      if (myEditor == null) {
        myEditor = ConsoleViewUtil.setupConsoleEditor(project, false, false);
        Disposer.register(
            project,
            () -> {
              EditorFactory.getInstance().releaseEditor(myEditor);
            });
      }
      return myEditor;
    }
  }

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    JPanel editorPanel =
        new JPanel(
            new AbstractLayoutManager() {
              private int getOffset() {
                return JBUIScale.scale(4);
              }

              @Override
              public Dimension preferredLayoutSize(Container parent) {
                Dimension size = parent.getComponent(0).getPreferredSize();
                return new Dimension(size.width + getOffset(), size.height);
              }

              @Override
              public void layoutContainer(Container parent) {
                int offset = getOffset();
                parent
                    .getComponent(0)
                    .setBounds(offset, 0, parent.getWidth() - offset, parent.getHeight());
              }
            }) {
          //      @Override
          //      public Color getBackground() {
          //        return ((EditorEx)editor).getBackgroundColor();
          //      }
        };

    editorPanel.add(CurrentFileEditor.get(project).getComponent());

    // Actually create panels
    final SimpleToolWindowPanel currentFilePanel = new SimpleToolWindowPanel(true);
    currentFilePanel.setContent(editorPanel);
    ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
    Content currentFileContent =
        contentFactory.createContent(currentFilePanel, "Current File", false);
    toolWindow.getContentManager().addContent(currentFileContent);
  }

  /**
   * Perform additional initialization routine here.
   *
   * @param toolWindow
   */
  @Override
  public void init(@NotNull ToolWindow toolWindow) {}

  /** Usually not invoked directly, see class javadoc. */
  @Override
  public void dispose() {}
}
