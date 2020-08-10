package ai.deepcode.jbplugin;

import com.intellij.testFramework.PlatformTestCase;

/**
 * See: https://www.jetbrains.org/intellij/sdk/docs/basics/testing_plugins/testing_plugins.html See:
 * https://www.jetbrains.org/intellij/sdk/docs/tutorials/writing_tests_for_plugins.html
 */
public abstract class MyPlatformTestCase extends PlatformTestCase {

  // !!! Will works only with already logged sessionToken
  protected static final String loggedToken =
          "7803ae6756d34b5cec056616fd59f4d6e499fce7fc3ce6db5cfd07f6e893e23a";

}
