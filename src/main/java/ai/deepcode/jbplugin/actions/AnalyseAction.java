package ai.deepcode.jbplugin.actions;

import ai.deepcode.javaclient.responses.*;
import ai.deepcode.jbplugin.DeepCodeNotifications;
import ai.deepcode.jbplugin.DeepCodeToolWindowFactory;
import ai.deepcode.jbplugin.utils.DeepCodeParams;
import ai.deepcode.jbplugin.utils.DeepCodeUtils;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AnalyseAction extends AnAction {
  private static final Logger LOG = LoggerFactory.getLogger("DeepCode.AnalyseAction");

  @Override
  public void update(AnActionEvent e) {
    // perform action if and only if EDITOR != null
    boolean enabled = e.getData(CommonDataKeys.EDITOR) != null;
    e.getPresentation().setEnabled(enabled);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    Project project = event.getProject();

    if (!DeepCodeParams.isLogged()) {
      DeepCodeNotifications.showLoginLink(project);
      return;
    }

    final PsiFile psiFile = event.getRequiredData(PlatformDataKeys.PSI_FILE);
    if (!DeepCodeParams.isSupportedFileFormat(psiFile)) {
      DeepCodeNotifications.showInfo(
          String.format(
              "Files with `%1$s` extension are not supported yet.",
              psiFile.getVirtualFile().getExtension()),
          project);
      return;
    }
    /*
        String loggedToken = DeepCodeParams.getSessionToken();
        //            "aeedc7d1c2656ea4b0adb1e215999f588b457cedf415c832a0209c9429c7636e";
        printMessage(project, "tokenId = " + loggedToken);

        //    Document doc = event.getRequiredData(CommonDataKeys.EDITOR).getDocument();
        String fileText = event.getRequiredData(PlatformDataKeys.FILE_TEXT);

        final String filePath = "/" + virtualFile.getPath();
        FileContent fileContent = new FileContent(filePath, fileText);
        FileContentRequest files = new FileContentRequest(Collections.singletonList(fileContent));
        CreateBundleResponse createBundleResponse;
        createBundleResponse = DeepCodeRestApi.createBundle(loggedToken, files);

        String bundleId = createBundleResponse.getBundleId();
        printMessage(
            project,
            String.format(
                "Create Bundle call return:\nStatus code [%1$d] %3$s \nBundleId: [%2$s]\n",
                createBundleResponse.getStatusCode(),
                createBundleResponse.getBundleId(),
                createBundleResponse.getStatusDescription()));

        GetAnalysisResponse getAnalysisResponse;
        getAnalysisResponse = DeepCodeRestApi.getAnalysis(loggedToken, bundleId);
        DeepCodeUtils.putAnalysisResponse(filePath, getAnalysisResponse);
    */

    GetAnalysisResponse getAnalysisResponse = DeepCodeUtils.getAnalysisResponse(psiFile);

    final String resultMsg =
        "Get Analysis call results: "
            + "\nreturns Status code: "
            + getAnalysisResponse.getStatusCode()
            + " "
            + getAnalysisResponse.getStatusDescription()
            + "\nreturns Body: "
            + getAnalysisResponse;
    System.out.println(resultMsg);

    for (String message : getPresentableAnalysisResults(psiFile, getAnalysisResponse)) {
      printMessage(project, message);
    }
  }

  private List<String> getPresentableAnalysisResults(
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

    List<String> result = new ArrayList<>();
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

        result.add(String.format("(%1$d:%2$d) %3$s", startRow, startCol, message));
      }
    }
    return result;
  }

  private void printMessage(Project project, String message) {
    EditorEx editor = DeepCodeToolWindowFactory.CurrentFileEditor.get(project);
    if (editor.isDisposed()) {
      return;
    }
    Document document = editor.getDocument();
    if (document.getTextLength() >= 0) {
      document.insertString(document.getTextLength(), message + "\n");
    }
  }
}
