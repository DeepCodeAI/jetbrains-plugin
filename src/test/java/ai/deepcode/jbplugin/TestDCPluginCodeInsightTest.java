package ai.deepcode.jbplugin;

import ai.deepcode.jbplugin.core.DeepCodeParams;
import ai.deepcode.jbplugin.core.LoginUtils;
import ai.deepcode.jbplugin.core.RunUtils;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

/**
 * See: https://www.jetbrains.org/intellij/sdk/docs/basics/testing_plugins/testing_plugins.html See:
 * https://www.jetbrains.org/intellij/sdk/docs/tutorials/writing_tests_for_plugins.html
 */
public class TestDCPluginCodeInsightTest extends BasePlatformTestCase {

  // !!! Will works only with already logged sessionToken
  private static final String loggedToken =
      "aeedc7d1c2656ea4b0adb1e215999f588b457cedf415c832a0209c9429c7636e";

  @Override
  protected String getTestDataPath() {
    return "src/test/testData";
  }

  public void testLogin() {
    final Project project = myFixture.getProject();

    DeepCodeParams.setSessionToken("blablabla");
    assertFalse("Login with malformed Token should fail", LoginUtils.isLogged(project, false));

    LoginUtils.requestNewLogin(project, false);
    assertFalse(
        "Login with newly requested but not yet logged token should fail",
        LoginUtils.isLogged(project, false));

    DeepCodeParams.setSessionToken(loggedToken);
    PropertiesComponent.getInstance(project).setValue("consentGiven", false);
    assertFalse("Login without Consent should fail", LoginUtils.isLogged(project, false));

    DeepCodeParams.setSessionToken(loggedToken);
    DeepCodeParams.setConsentGiven(project);
    assertTrue(
        "Login with logged Token and confirmed Consent should pass",
        LoginUtils.isLogged(project, false));
  }

  public void testHighlighting_Java() {
    DeepCodeParams.setSessionToken(loggedToken);
    DeepCodeParams.setConsentGiven(myFixture.getProject());
    myFixture.configureByFile("AnnotatorTest.java");
    //fixme: delay to let annotators do the job
    RunUtils.delay(2000);
    myFixture.checkHighlighting(true, true, true, true);
  }

  public void testHighlighting_CPP() {
    DeepCodeParams.setSessionToken(loggedToken);
    DeepCodeParams.setConsentGiven(myFixture.getProject());
    myFixture.configureByFile("AnnotatorTest.cpp");
    //fixme: delay to let annotators do the job
    RunUtils.delay(2000);
    myFixture.checkHighlighting(true, true, true, false);
  }

  public void testHighlighting_JavaScript() {
    DeepCodeParams.setSessionToken(loggedToken);
    DeepCodeParams.setConsentGiven(myFixture.getProject());
    myFixture.configureByFile("AnnotatorTest1.js");
    myFixture.checkHighlighting(true, true, true, false);
  }
}
