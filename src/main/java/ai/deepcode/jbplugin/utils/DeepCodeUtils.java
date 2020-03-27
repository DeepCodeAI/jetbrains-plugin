package ai.deepcode.jbplugin.utils;

import ai.deepcode.javaclient.DeepCodeRestApi;
import ai.deepcode.javaclient.requests.FileContent;
import ai.deepcode.javaclient.requests.FileContentRequest;
import ai.deepcode.javaclient.responses.CreateBundleResponse;
import ai.deepcode.javaclient.responses.GetAnalysisResponse;
import ai.deepcode.jbplugin.actions.AnalyseAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeAnyChangeAbstractAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DeepCodeUtils {

  // todo: keep few latest file versions (Guava com.google.common.cache.CacheBuilder ?)
  private static final Map<PsiFile, GetAnalysisResponse> mapFile2Response =
      new ConcurrentHashMap<>();

  private static Set<Project> projects = ConcurrentHashMap.newKeySet();

  /** Add File Listener to clear caches for file if it was changed. */
  private static void addFileListener(@NotNull final Project project) {
    if (!projects.contains(project)) {
      PsiManager.getInstance(project)
              .addPsiTreeChangeListener(
                      new PsiTreeAnyChangeAbstractAdapter() {
                        @Override
                        protected void onChange(@Nullable PsiFile file) {
                          if (file != null) {
                            mapFile2Response.remove(file);
                          }
                        }
                      });
      projects.add(project);
    }
  }

  @NotNull
  public static GetAnalysisResponse getAnalysisResponse(@NotNull PsiFile psiFile) {
    System.out.println(psiFile+ "@" + Integer.toHexString(psiFile.hashCode()));
    addFileListener(psiFile.getProject());

    GetAnalysisResponse response = mapFile2Response.get(psiFile);
    if (response != null) {
      if (response.getStatus().equals("DONE")) {
        return response;
      } else {
        mapFile2Response.remove(psiFile);
      }
    }
    response = createResponse(psiFile);
    mapFile2Response.put(psiFile, response);
    return response;
  }

  @NotNull
  private static GetAnalysisResponse createResponse(@NotNull PsiFile psiFile) {
    String loggedToken = DeepCodeParams.getSessionToken();
    FileContent fileContent =
        new FileContent("/" + psiFile.getVirtualFile().getPath(), psiFile.getText());
    FileContentRequest files = new FileContentRequest(Collections.singletonList(fileContent));
    CreateBundleResponse createBundleResponse = DeepCodeRestApi.createBundle(loggedToken, files);
    GetAnalysisResponse result = DeepCodeRestApi.getAnalysis(loggedToken, createBundleResponse.getBundleId());
    for (int i = 0; i < 10; i++) {
      if (result.getStatus().equals("DONE")) {
        return result;
      }
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        e.printStackTrace();
        Thread.currentThread().interrupt();
      }
      result = DeepCodeRestApi.getAnalysis(loggedToken, createBundleResponse.getBundleId());
    }
    // todo: show notification
    return result;
  }

  public static void updateCurrentFilePanel(PsiFile psiFile) {
    ApplicationManager.getApplication()
            .invokeLater(
                    () ->
                            WriteCommandAction.runWriteCommandAction(
                                    psiFile.getProject(), () -> AnalyseAction.updateCurrentFilePanel(psiFile)));
  }

}
