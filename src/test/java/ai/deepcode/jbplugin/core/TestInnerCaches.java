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
    DeepCodeParams.setSessionToken(loggedToken);
    DeepCodeParams.setConsentGiven(project);

    myFixture.configureByFile("AnnotatorTest_ValidJS.js");
    psiTestFile = myFixture.getFile();

    runInBackground(
        project,
        () ->
            AnalysisData.updateCachedResultsForFiles(
                project, Collections.singleton(psiTestFile), Collections.emptyList()));

    AnalysisData.waitForUpdateAnalysisFinish();
    //RunUtils.delay(1000);
  }

  public void testProjectInCache() {
    final Set<Project> allCachedProject = AnalysisData.getAllCachedProject();
    assertTrue(
        "Current Project should be in cache.",
        allCachedProject.size() == 1 && allCachedProject.contains(project));
  }

  public void testFileInCache() {
    assertTrue("Test file is not in cache", AnalysisData.isFileInCache(psiTestFile));

    final Set<PsiFile> filesWithSuggestions = AnalysisData.getAllFilesWithSuggestions(project);
    assertFalse("List of Files with suggestions is empty", filesWithSuggestions.isEmpty());
    assertTrue("Test file has no suggestions in cache", filesWithSuggestions.contains(psiTestFile));
  }
}
