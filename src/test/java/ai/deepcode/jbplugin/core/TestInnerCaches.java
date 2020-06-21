package ai.deepcode.jbplugin.core;

import ai.deepcode.jbplugin.MyBasePlatformTestCase;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

import java.util.Collections;
import java.util.Set;

import static ai.deepcode.jbplugin.core.RunUtils.runInBackground;

public class TestInnerCaches extends MyBasePlatformTestCase {
  PsiFile psiTestFile;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    DeepCodeParams.getInstance().setSessionToken(loggedToken);
    DeepCodeParams.getInstance().setConsentGiven(project);

    myFixture.configureByFile("AnnotatorTest_ValidJS.js");
    psiTestFile = myFixture.getFile();

    runInBackground(
        project,
        "Test analysis",
        (progress) ->
            AnalysisData.getInstance()
                .updateCachedResultsForFiles(
                    project,
                    Collections.singleton(psiTestFile),
                    Collections.emptyList(),
                    progress));

    AnalysisData.getInstance().waitForUpdateAnalysisFinish(null);
    // RunUtils.delay(1000);
  }

  public void testProjectInCache() {
    final Set<Object> allCachedProject = AnalysisData.getInstance().getAllCachedProject();
    assertTrue(
        "Current Project should be in cache.",
        allCachedProject.size() == 1 && allCachedProject.contains(project));
  }

  public void testFileInCache() {
    assertTrue("Test file is not in cache", AnalysisData.getInstance().isFileInCache(psiTestFile));

    final Set<Object> filesWithSuggestions =
        AnalysisData.getInstance().getAllFilesWithSuggestions(project);
    assertFalse("List of Files with suggestions is empty", filesWithSuggestions.isEmpty());
    assertTrue("Test file has no suggestions in cache", filesWithSuggestions.contains(psiTestFile));
  }
}
