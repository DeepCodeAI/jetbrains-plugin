package ai.deepcode.jbplugin.utils;

import ai.deepcode.javaclient.DeepCodeRestApi;
import ai.deepcode.javaclient.requests.FileContent;
import ai.deepcode.javaclient.requests.FileContentRequest;
import ai.deepcode.javaclient.responses.CreateBundleResponse;
import ai.deepcode.javaclient.responses.GetAnalysisResponse;
import ai.deepcode.jbplugin.DeepCodeNotifications;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DeepCodeUtils {

  private static final Map<PsiFile, GetAnalysisResponse> mapFile2Response =
      new ConcurrentHashMap<>();

  /*
    public static void putAnalysisResponse(@NotNull String filePath, @NotNull GetAnalysisResponse response){
      mapFile2Response.put(filePath, response);
    }
  */

  public static void removeAnalysisResponse(@NotNull PsiFile psiFile) {
    mapFile2Response.remove(psiFile);
  }

  @NotNull
  public static GetAnalysisResponse getAnalysisResponse(@NotNull PsiFile psiFile) {
    GetAnalysisResponse response = mapFile2Response.get(psiFile);
    if (response != null) {
      if (response.getStatus().equals("DONE")) {
        return response;
      } else {
        mapFile2Response.remove(psiFile);
      }
    }
    return createResponse(psiFile);
  }

  @NotNull
  private static GetAnalysisResponse createResponse(@NotNull PsiFile psiFile) {
    String loggedToken = DeepCodeParams.getSessionToken();
    FileContent fileContent =
        new FileContent("/" + psiFile.getVirtualFile().getPath(), psiFile.getText());
    FileContentRequest files = new FileContentRequest(Collections.singletonList(fileContent));
    CreateBundleResponse createBundleResponse = DeepCodeRestApi.createBundle(loggedToken, files);

    return DeepCodeRestApi.getAnalysis(loggedToken, createBundleResponse.getBundleId());
  }
}
