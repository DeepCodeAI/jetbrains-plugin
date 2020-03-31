package ai.deepcode.jbplugin.actions;

import ai.deepcode.jbplugin.DeepCodeNotifications;
import ai.deepcode.jbplugin.utils.DeepCodeParams;
import ai.deepcode.jbplugin.utils.DeepCodeUtils;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


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
    final PsiFile psiFile = event.getRequiredData(PlatformDataKeys.PSI_FILE);
    Project project = psiFile.getProject();

    if (!DeepCodeParams.isLogged()) {
      DeepCodeNotifications.showLoginLink(project);
      return;
    }

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
    DeepCodeUtils.asyncUpdateCurrentFilePanel(psiFile);
  }
}
