package ai.deepcode.jbplugin;

import ai.deepcode.jbplugin.core.DeepCodeParams;
import ai.deepcode.jbplugin.core.PDU;
import org.slf4j.LoggerFactory;

public class TestAnnotatorForCPP extends MyBasePlatformTestCase {

 public void testHighlighting_CPP() {
   LoggerFactory.getLogger(this.getClass()).info("-------------------testHighlighting_CPP--------------------");
   DeepCodeParams.getInstance().setSessionToken(loggedToken);
    DeepCodeParams.getInstance().setConsentGiven(project);
    myFixture.configureByFile("AnnotatorTest.cpp");
    //fixme: delay to let annotators do the job
    PDU.getInstance().delay(2000, null);
    myFixture.checkHighlighting(true, true, true, false);
  }

}
