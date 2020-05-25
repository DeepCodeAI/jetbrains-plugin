package ai.deepcode.jbplugin;

import ai.deepcode.jbplugin.core.DeepCodeParams;
import ai.deepcode.jbplugin.core.RunUtils;

public class TestAnnotatorForCPP extends MyBasePlatformTestCase {

 public void testHighlighting_CPP() {
    DeepCodeParams.setSessionToken(loggedToken);
    DeepCodeParams.setConsentGiven(project);
    myFixture.configureByFile("AnnotatorTest.cpp");
    //fixme: delay to let annotators do the job
    RunUtils.delay(2000);
    myFixture.checkHighlighting(true, true, true, false);
  }

}
