package ai.deepcode.jbplugin.core;

import ai.deepcode.jbplugin.MyBasePlatformTestCase;
import com.intellij.psi.PsiFile;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Set;

public class TestInnerCaches extends MyBasePlatformTestCase {
  PsiFile psiTestFile;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    LoggerFactory.getLogger(this.getClass()).info("-------------------TestInnerCaches.setUp--------------------");

    DeepCodeParams.getInstance().setSessionToken(loggedToken);
    DeepCodeParams.getInstance().setConsentGiven(project);

    myFixture.configureByFile("AnnotatorTest_ValidCPP.cpp");
    psiTestFile = myFixture.getFile();

    RunUtils.getInstance().runInBackground(
        project,
        "Test analysis",
        (progress) ->
            AnalysisData.getInstance()
                .updateCachedResultsForFiles(
                    project,
                    Collections.singleton(psiTestFile),
                    Collections.emptyList(),
                    progress));

    AnalysisData.getInstance().waitForUpdateAnalysisFinish(project, null);
    // RunUtils.delay(1000);
  }

  public void testProjectInCache() {
    LoggerFactory.getLogger(this.getClass()).info("-------------------testProjectInCache--------------------");
    //delay to let caches update process finish
    PDU.getInstance().delay(2000, null);
    final Set<Object> allCachedProject = AnalysisData.getInstance().getAllCachedProject();
    assertTrue(
        "Current Project should be in cache.",
        allCachedProject.size() == 1 && allCachedProject.contains(project));
  }

  public void testFileInCache() {
    LoggerFactory.getLogger(this.getClass()).info("-------------------testFileInCache--------------------");
    assertTrue("Test file is not in cache", AnalysisData.getInstance().isFileInCache(psiTestFile));
    final Set<Object> filesWithSuggestions =
        AnalysisData.getInstance().getAllFilesWithSuggestions(project);
    assertFalse("List of Files with suggestions is empty", filesWithSuggestions.isEmpty());
    assertTrue("Test file has no suggestions in cache", filesWithSuggestions.contains(psiTestFile));
  }
}
