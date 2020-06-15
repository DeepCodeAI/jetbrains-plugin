package ai.deepcode.javaclient.core;

import ai.deepcode.javaclient.DeepCodeRestApi;
import org.jetbrains.annotations.NotNull;

public abstract class DeepCodeParamsBase {

  // Settings
  private boolean isEnable;
  private String apiUrl;
  private boolean useLinter;
  private int minSeverity;
  private String sessionToken;

  // Inner params
  private String loginUrl;
  private String ideProductName;

  protected DeepCodeParamsBase(
      boolean isEnable,
      String apiUrl,
      boolean useLinter,
      int minSeverity,
      String sessionToken,
      String loginUrl,
      String ideProductName) {
    this.isEnable = isEnable;
    this.apiUrl = apiUrl;
    this.useLinter = useLinter;
    this.minSeverity = minSeverity;
    this.sessionToken = sessionToken;
    this.loginUrl = loginUrl;
    this.ideProductName = ideProductName;
  }

  public void clearLoginParams() {
    setSessionToken("");
    setLoginUrl("");
  }

  @NotNull
  public String getSessionToken() {
    return sessionToken;
  }

  public void setSessionToken(String sessionToken) {
    this.sessionToken = sessionToken;
  }

  @NotNull
  public String getLoginUrl() {
    return loginUrl;
  }

  public void setLoginUrl(String loginUrl) {
    this.loginUrl = loginUrl;
  }

  public boolean useLinter() {
    return useLinter;
  }

  public void setUseLinter(boolean useLinter) {
    this.useLinter = useLinter;
  }

  public int getMinSeverity() {
    return minSeverity;
  }

  public void setMinSeverity(int minSeverity) {
    this.minSeverity = minSeverity;
  }

  @NotNull
  public String getApiUrl() {
    return apiUrl;
  }

  public void setApiUrl(@NotNull String apiUrl) {
    if (apiUrl.isEmpty()) apiUrl = "https://www.deepcode.ai/";
    if (!apiUrl.endsWith("/")) apiUrl += "/";
    if (apiUrl.equals(this.apiUrl)) return;
    this.apiUrl = apiUrl;
    DeepCodeRestApi.setBaseUrl(apiUrl);
  }

  public boolean isEnable() {
    return isEnable;
  }

  public void setEnable(boolean isEnable) {
    this.isEnable = isEnable;
  }

  public abstract boolean consentGiven(@NotNull Object project);

  public abstract void setConsentGiven(@NotNull Object project);

  public String getIdeProductName() {
    return ideProductName;
  }
}
