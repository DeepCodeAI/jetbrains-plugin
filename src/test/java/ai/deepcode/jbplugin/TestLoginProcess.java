package ai.deepcode.jbplugin;

import ai.deepcode.jbplugin.core.DeepCodeParams;
import ai.deepcode.jbplugin.core.LoginUtils;
import ai.deepcode.jbplugin.core.RunUtils;
import com.intellij.ide.util.PropertiesComponent;
import org.slf4j.LoggerFactory;

public class TestLoginProcess extends MyBasePlatformTestCase {

  public void testMalformedToken() {
    LoggerFactory.getLogger(this.getClass()).info("-------------------testMalformedToken--------------------");
    DeepCodeParams.getInstance().setSessionToken("blablabla");
    assertFalse(
        "Login with malformed Token should fail",
        LoginUtils.getInstance().isLogged(project, false));
  }

  public void testNotLoggedToken() {
    LoggerFactory.getLogger(this.getClass()).info("-------------------testNotLoggedToken--------------------");
    // need to run as a background process due to synchronized execution (??) in test environment.
    RunUtils.getInstance().runInBackground(
        project,
        "New Login Request",
        (progress) -> LoginUtils.getInstance().requestNewLogin(project, false));
    assertFalse(
        "Login with newly requested but not yet logged token should fail",
        LoginUtils.getInstance().isLogged(project, false));
  }

  public void testNotGivenConsent() {
    LoggerFactory.getLogger(this.getClass()).info("-------------------testNotGivenConsent--------------------");
    DeepCodeParams.getInstance().setSessionToken(loggedToken);
    PropertiesComponent.getInstance(project).setValue("consentGiven", false);
    assertFalse(
        "Login without Consent should fail", LoginUtils.getInstance().isLogged(project, false));
  }

  public void testLoggedTokenAndGivenConsent() {
    LoggerFactory.getLogger(this.getClass()).info("-------------------testLoggedTokenAndGivenConsent--------------------");
    DeepCodeParams.getInstance().setSessionToken(loggedToken);
    DeepCodeParams.getInstance().setConsentGiven(project);
    assertTrue(
        "Login with logged Token and confirmed Consent should pass",
        LoginUtils.getInstance().isLogged(project, false));
  }
}
