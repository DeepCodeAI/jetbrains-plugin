package ai.deepcode.jbplugin;

import ai.deepcode.jbplugin.core.DeepCodeParams;
import ai.deepcode.jbplugin.core.LoginUtils;
import com.intellij.ide.util.PropertiesComponent;

public class TestLoginProcess extends MyBasePlatformTestCase {

  public void testMalformedToken() {
    DeepCodeParams.getInstance().setSessionToken("blablabla");
    assertFalse("Login with malformed Token should fail", LoginUtils.isLogged(project, false));
  }

  public void testNotLoggedToken() {
    LoginUtils.requestNewLogin(project, false);
    assertFalse(
        "Login with newly requested but not yet logged token should fail",
        LoginUtils.isLogged(project, false));
  }

  public void testNotGivenConsent() {
    DeepCodeParams.getInstance().setSessionToken(loggedToken);
    PropertiesComponent.getInstance(project).setValue("consentGiven", false);
    assertFalse("Login without Consent should fail", LoginUtils.isLogged(project, false));
  }

  public void testLoggedTokenAndGivenConsent() {
    DeepCodeParams.getInstance().setSessionToken(loggedToken);
    DeepCodeParams.getInstance().setConsentGiven(project);
    assertTrue(
        "Login with logged Token and confirmed Consent should pass",
        LoginUtils.isLogged(project, false));
  }
}
