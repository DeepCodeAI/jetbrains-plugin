package ai.deepcode.jbplugin;

import ai.deepcode.jbplugin.core.DeepCodeParams;
import org.slf4j.LoggerFactory;

public class TestAnnotatorForJavaScript extends MyBasePlatformTestCase {

  public void testHighlighting_JavaScript() {
    LoggerFactory.getLogger(this.getClass()).info("-------------------testHighlighting_JavaScript--------------------");
    DeepCodeParams.getInstance().setSessionToken(loggedToken);
    DeepCodeParams.getInstance().setConsentGiven(project);
    myFixture.configureByFile("AnnotatorTest.js");
    myFixture.checkHighlighting(true, true, true, false);
  }

}
