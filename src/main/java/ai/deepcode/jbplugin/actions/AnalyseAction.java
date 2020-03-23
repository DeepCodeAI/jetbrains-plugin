package ai.deepcode.jbplugin.actions;

import ai.deepcode.javaclient.DeepCodeRestApi;
import ai.deepcode.javaclient.requests.FileContent;
import ai.deepcode.javaclient.requests.FileContentRequest;
import ai.deepcode.javaclient.responses.CreateBundleResponse;
import ai.deepcode.javaclient.responses.GetAnalysisResponse;
import ai.deepcode.jbplugin.DeepCodeNotifications;
import ai.deepcode.jbplugin.utils.DeepCodeParams;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

import static org.junit.Assert.assertNotNull;

public class AnalyseAction extends AnAction {

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
//    Document doc = event.getRequiredData(CommonDataKeys.EDITOR).getDocument();
    String fileText = event.getRequiredData(PlatformDataKeys.FILE_TEXT);
    final PsiFile psiFile = event.getRequiredData(PlatformDataKeys.PSI_FILE);
    String fileExtension = psiFile.getVirtualFile().getExtension();
    if (!DeepCodeParams.getSupportedExtensions(project).contains(fileExtension)) {
      String message =
          String.format("Files with `%1$s` extension are not supported yet.", fileExtension);
      DeepCodeNotifications.showInfo(message, project);
      return;
    }

    String loggedToken = DeepCodeParams.getSessionToken();
    //            "aeedc7d1c2656ea4b0adb1e215999f588b457cedf415c832a0209c9429c7636e";
    FileContent fileContent = new FileContent("/test.js", fileText);
    FileContentRequest files = new FileContentRequest(Collections.singletonList(fileContent));
    CreateBundleResponse createBundleResponse;
    createBundleResponse = DeepCodeRestApi.createBundle(loggedToken, files);
    assertNotNull(createBundleResponse);
    String bundleId = createBundleResponse.getBundleId();
    System.out.printf(
        "Create Bundle call return:\nStatus code [%1$d] %3$s \nBundleId: [%2$s]\n",
        createBundleResponse.getStatusCode(),
        createBundleResponse.getBundleId(),
        createBundleResponse.getStatusDescription());

    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    GetAnalysisResponse getAnalysisResponse;
    getAnalysisResponse = DeepCodeRestApi.getAnalysis(loggedToken, bundleId);
    assertNotNull(getAnalysisResponse);
    System.out.println(
        "Get Analysis call results: "
            + "\nreturns Status code: "
            + getAnalysisResponse.getStatusCode()
            + " "
            + getAnalysisResponse.getStatusDescription()
            + "\nreturns Body: "
            + getAnalysisResponse);
  }
}
