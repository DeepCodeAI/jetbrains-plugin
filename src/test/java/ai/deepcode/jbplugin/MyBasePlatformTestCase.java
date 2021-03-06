package ai.deepcode.jbplugin;

import ai.deepcode.jbplugin.core.DeepCodeParams;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.slf4j.LoggerFactory;

/**
 * See: https://www.jetbrains.org/intellij/sdk/docs/basics/testing_plugins/testing_plugins.html See:
 * https://www.jetbrains.org/intellij/sdk/docs/tutorials/writing_tests_for_plugins.html
 */
public abstract class MyBasePlatformTestCase extends BasePlatformTestCase {
  protected Project project;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    LoggerFactory.getLogger(this.getClass()).info("-------------------MyBasePlatformTestCase.setUp--------------------\n");
    project = myFixture.getProject();
    DeepCodeParams.getInstance().setUpdateMode(DeepCodeParams.UpdateMode.INTERACTIVE_MODE);
  }

  // !!! Will works only with already logged sessionToken
  protected static final String loggedToken = System.getenv("DEEPCODE_API_KEY");

  @Override
  protected String getTestDataPath() {
    return "src/test/testData";
  }
}
