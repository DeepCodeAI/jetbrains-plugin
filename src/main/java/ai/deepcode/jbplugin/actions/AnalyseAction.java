package ai.deepcode.jbplugin.actions;

import ai.deepcode.javaclient.DeepCodeRestApi;
import ai.deepcode.javaclient.requests.FileContent;
import ai.deepcode.javaclient.requests.FileContentRequest;
import ai.deepcode.javaclient.responses.CreateBundleResponse;
import ai.deepcode.javaclient.responses.GetAnalysisResponse;
import ai.deepcode.jbplugin.DeepCodeStartupActivity;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Document;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collections;

import static org.junit.Assert.assertNotNull;

public class AnalyseAction extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    Document doc = event.getRequiredData(CommonDataKeys.EDITOR).getDocument();
    String fileText = event.getRequiredData(PlatformDataKeys.FILE_TEXT);

    String loggedToken = DeepCodeStartupActivity.getSessionToken();
//            "aeedc7d1c2656ea4b0adb1e215999f588b457cedf415c832a0209c9429c7636e";
    FileContent fileContent = new FileContent("/test.js", fileText);
    FileContentRequest files = new FileContentRequest(Collections.singletonList(fileContent));
    CreateBundleResponse createBundleResponse;
    try {
      createBundleResponse = DeepCodeRestApi.createBundle(loggedToken, files);
    } catch (IOException e) {
      e.printStackTrace();
      return;
    }
    assertNotNull(createBundleResponse);
    String bundleId = createBundleResponse.getBundleId();
    System.out.printf(
            "Create Bundle call return:\nStatus code [%1$d] \nBundleId: [%2$s]\n",
            createBundleResponse.getStatusCode(), createBundleResponse.getBundleId());


    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    GetAnalysisResponse getAnalysisResponse;
    try {
      getAnalysisResponse = DeepCodeRestApi.getAnalysis(loggedToken, bundleId);
    } catch (IOException e) {
      e.printStackTrace();
      return;
    }
    assertNotNull(getAnalysisResponse);
    System.out.println(
            "Get Analysis call results: "
                    + "\nreturns Status code: "
                    + getAnalysisResponse.getStatusCode()
                    + "\nreturns Body: "
                    + getAnalysisResponse);

  }
}
