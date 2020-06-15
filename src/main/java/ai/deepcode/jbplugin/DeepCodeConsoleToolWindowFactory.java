package ai.deepcode.jbplugin;

import ai.deepcode.javaclient.core.MyTextRange;
import ai.deepcode.jbplugin.core.AnalysisData;
import ai.deepcode.jbplugin.core.DeepCodeUtils;
import ai.deepcode.javaclient.core.SuggestionForFile;
import com.intellij.execution.impl.ConsoleViewUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.psi.PsiFile;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.AbstractLayoutManager;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class DeepCodeConsoleToolWindowFactory implements ToolWindowFactory, Disposable {
  private static final Logger LOG = LoggerFactory.getLogger("DeepCodeToolWindowFactory");
  private static PsiFile myCurrentFile;

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    final ContentManager contentManager = toolWindow.getContentManager();
    final ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();

    // Current File panel
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
       };

    editorPanel.add(CurrentFileEditor.get(project).getComponent());

    // todo CurrentFilePanel
    final SimpleToolWindowPanel currentFilePanel = new SimpleToolWindowPanel(true);
    currentFilePanel.setContent(editorPanel);
    Content currentFileContent =
        contentFactory.createContent(currentFilePanel, "Current File", false);
    contentManager.addContent(currentFileContent);

    // Update CurrentFile Panel if file was edited
/*
    PsiManager.getInstance(project)
        .addPsiTreeChangeListener(
            new PsiTreeChangeAdapter() {
              @Override
              public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
                PsiFile file = event.getFile();
                if (file != null && file.equals(myCurrentFile)) {
                  DeepCodeUtils.asyncUpdateCurrentFilePanel(file);
                }
              }
            });
*/

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

  // ------------------------------------------------------------------------------

  private static class CurrentFileEditor {

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

  public static void updateCurrentFilePanel(@NotNull PsiFile psiFile) {
    myCurrentFile = psiFile;
    Project project = psiFile.getProject();
    cleanMessages(project);
    if (!DeepCodeUtils.isSupportedFileFormat(psiFile)) return;
    List<SuggestionForFile> suggestions = AnalysisData.getInstance().getAnalysis(psiFile);
    for (SuggestionForFile suggestion : suggestions) {
      printMessage(project, suggestion.getMessage());
      for (MyTextRange range : suggestion.getRanges()) {
        printMessage(project, "  " + range);
      }
    }
  }

  private static void printMessage(Project project, String message) {
    EditorEx editor = CurrentFileEditor.get(project);
    if (editor.isDisposed()) {
      return;
    }
    Document document = editor.getDocument();
    if (document.getTextLength() >= 0) {
      document.insertString(document.getTextLength(), message + "\n");
    }
  }

  private static void cleanMessages(Project project) {
    EditorEx editor = CurrentFileEditor.get(project);
    if (editor.isDisposed()) {
      return;
    }
    Document document = editor.getDocument();
    if (document.getTextLength() >= 0) {
      document.setText("");
    }
  }
}
