package ai.deepcode.jbplugin;

import ai.deepcode.jbplugin.core.DeepCodeParams;
import ai.deepcode.jbplugin.core.RunUtils;

public class TestAnnotatorForJava extends MyBasePlatformTestCase {

  public void testHighlighting_Java() {
    DeepCodeParams.getInstance().setSessionToken(loggedToken);
    DeepCodeParams.getInstance().setConsentGiven(project);
    myFixture.configureByFile("AnnotatorTest.java");
    //fixme: delay to let annotators do the job
    RunUtils.delay(2000);
    myFixture.checkHighlighting(true, true, true, true);
  }

}
