package ai.deepcode.jbplugin;

import ai.deepcode.jbplugin.core.DeepCodeParams;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * See: https://www.jetbrains.org/intellij/sdk/docs/basics/testing_plugins/testing_plugins.html See:
 * https://www.jetbrains.org/intellij/sdk/docs/tutorials/writing_tests_for_plugins.html
 */
public abstract class MyBasePlatformTestCase extends BasePlatformTestCase {
  protected Project project;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    project = myFixture.getProject();
  }

  // !!! Will works only with already logged sessionToken
  protected static final String loggedToken =
      "aeedc7d1c2656ea4b0adb1e215999f588b457cedf415c832a0209c9429c7636e";

  @Override
  protected String getTestDataPath() {
    return "src/test/testData";
  }
}
