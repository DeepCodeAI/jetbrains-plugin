package ai.deepcode.jbplugin;

import ai.deepcode.javaclient.responses.*;
import ai.deepcode.jbplugin.ui.panels.ProjectFilesPanel;
import ai.deepcode.jbplugin.utils.DeepCodeParams;
import ai.deepcode.jbplugin.utils.AnalysisData;
import ai.deepcode.jbplugin.utils.DeepCodeUtils;
import com.intellij.execution.impl.ConsoleViewUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeAnyChangeAbstractAdapter;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.AbstractLayoutManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class DeepCodeToolWindowFactory implements ToolWindowFactory, Disposable {
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
          //      @Override
          //      public Color getBackground() {
          //        return ((EditorEx)editor).getBackgroundColor();
          //      }
        };

    editorPanel.add(CurrentFileEditor.get(project).getComponent());

    // todo CurrentFilePanel
    final SimpleToolWindowPanel currentFilePanel = new SimpleToolWindowPanel(true);
    currentFilePanel.setContent(editorPanel);
    Content currentFileContent =
        contentFactory.createContent(currentFilePanel, "Current File", false);
    contentManager.addContent(currentFileContent);

    //Update CurrentFile Panel if file was edited
    PsiManager.getInstance(project)
            .addPsiTreeChangeListener(
                    new PsiTreeAnyChangeAbstractAdapter() {
                      @Override
                      protected void onChange(@Nullable PsiFile file) {
                        if (file != null && file.equals(myCurrentFile)) {
                          updateCurrentFilePanel(file);
                        }
                      }
                    });


    // Project Files panel
    final ProjectFilesPanel projectFilesPanel = new ProjectFilesPanel(true);
    Content projectFilesContent =
            contentFactory.createContent(projectFilesPanel, "Project Files", false);
    contentManager.addContent(projectFilesContent);

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
    if (!DeepCodeParams.isSupportedFileFormat(psiFile)) return;
    List<AnalysisData.SuggestionForFile> suggestions = AnalysisData.getAnalysis(psiFile);
    for (AnalysisData.SuggestionForFile suggestion : suggestions) {
      printMessage(project, suggestion.getMessage());
      for (TextRange range : suggestion.getRanges()) {
        printMessage(project, "  " + range);
      }
    }
//    for (String message : getPresentableAnalysisResults(psiFile, suggestions)) {
//      printMessage(project, message);
//    }
  }

  private static List<String> getPresentableAnalysisResults(
          @NotNull PsiFile psiFile, GetAnalysisResponse response) {
    if (!response.getStatus().equals("DONE")) return Collections.emptyList();
    AnalysisResults analysisResults = response.getAnalysisResults();
    if (analysisResults == null) {
      LOG.error("AnalysisResults is null for: ", response);
      return Collections.emptyList();
    }
    FileSuggestions fileSuggestions =
            analysisResults.getFiles().get("/" + psiFile.getVirtualFile().getPath());
    if (fileSuggestions == null) return Collections.emptyList();
    final Suggestions suggestions = analysisResults.getSuggestions();
    if (suggestions == null) {
      LOG.error("Suggestions is empty for: ", response);
      return Collections.emptyList();
    }
    Document document = psiFile.getViewProvider().getDocument();
    if (document == null) {
      LOG.error("Document not found for: ", psiFile, response);
      return Collections.emptyList();
    }

    TreeMap<Pair<Integer, Integer>, String> resultMap =
            new TreeMap<>(
                    Comparator.comparingInt((Pair<Integer, Integer> p) -> p.getFirst())
                            .thenComparingInt(p -> p.getSecond()));

    for (String suggestionIndex : fileSuggestions.keySet()) {
      final Suggestion suggestion = suggestions.get(suggestionIndex);
      if (suggestion == null) {
        LOG.error("Suggestion not found for: ", suggestionIndex, response);
        return Collections.emptyList();
      }

      final String message = suggestion.getMessage();

      for (FileRange fileRange : fileSuggestions.get(suggestionIndex)) {
        final int startRow = fileRange.getRows().get(0);
        final int endRow = fileRange.getRows().get(1);
        final int startCol = fileRange.getCols().get(0) - 1; // inclusive
        final int endCol = fileRange.getCols().get(1);

        resultMap.put(new Pair<>(startRow, startCol), message);
      }
    }
    return resultMap.entrySet().stream()
            .map(entry -> entry.getKey().toString() + " " + entry.getValue())
            .collect(Collectors.toList());
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
