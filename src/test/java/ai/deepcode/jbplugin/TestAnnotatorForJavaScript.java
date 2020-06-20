package ai.deepcode.jbplugin;

import ai.deepcode.jbplugin.core.DeepCodeParams;

public class TestAnnotatorForJavaScript extends MyBasePlatformTestCase {

  public void testHighlighting_JavaScript() {
    DeepCodeParams.getInstance().setSessionToken(loggedToken);
    DeepCodeParams.getInstance().setConsentGiven(project);
    myFixture.configureByFile("AnnotatorTest.js");
    myFixture.checkHighlighting(true, true, true, false);
  }

}
