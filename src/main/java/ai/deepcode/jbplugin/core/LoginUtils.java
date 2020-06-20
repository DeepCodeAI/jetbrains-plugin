package ai.deepcode.jbplugin.core;

import ai.deepcode.javaclient.core.LoginUtilsBase;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;

public class LoginUtils extends LoginUtilsBase {

  private static final LoginUtils INSTANCE = new LoginUtils();

  public static LoginUtils getInstance() {
    return INSTANCE;
  }

  private LoginUtils() {
    super(
            PDU.getInstance(),
            DeepCodeParams.getInstance(),
            AnalysisData.getInstance(),
            DCLogger.getInstance());
  }

  private static final String userAgent =
      "JetBrains-"
          + ApplicationNamesInfo.getInstance().getProductName()
          + "-"
          + ApplicationInfo.getInstance().getFullVersion();

  @Override
  protected String getUserAgent() {
    return userAgent;
  }
}
