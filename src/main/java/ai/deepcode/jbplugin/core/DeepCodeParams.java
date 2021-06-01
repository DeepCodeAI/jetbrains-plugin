package ai.deepcode.jbplugin.core;

import ai.deepcode.javaclient.DeepCodeRestApi;
import ai.deepcode.javaclient.core.DeepCodeParamsBase;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Calendar;

public class DeepCodeParams extends DeepCodeParamsBase {

  private static final DeepCodeParams INSTANCE = new DeepCodeParams();

  public static DeepCodeParams getInstance() {
    return INSTANCE;
  }

  public static enum UpdateMode {
    INTERACTIVE_MODE,
    ON_SAVE_MODE,
    MANUAL_MODE
  }

  private UpdateMode updateMode;

  // TODO https://www.jetbrains.org/intellij/sdk/docs/basics/persisting_sensitive_data.html
  private final PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();

  private DeepCodeParams() {
    super(
        PropertiesComponent.getInstance().getBoolean("isEnable", true),
        PropertiesComponent.getInstance().getValue("apiUrl", "https://www.deepcode.ai/"),
        PropertiesComponent.getInstance().getBoolean("useLinter"),
        PropertiesComponent.getInstance().getInt("minSeverity", 1),
        PropertiesComponent.getInstance().getValue("sessionToken", ""),
        PropertiesComponent.getInstance().getValue("loginUrl", ""),
        ApplicationNamesInfo.getInstance().getProductName());
    DeepCodeRestApi.setBaseUrl(getApiUrl());

    String pastIdeProductName = propertiesComponent.getValue("ideProductName", "");
    String ideProductName = getIdeProductName();
    if (!pastIdeProductName.equals(ideProductName)) {
      clearLoginParams();
      propertiesComponent.setValue("ideProductName", ideProductName);
    }
    updateMode =
        UpdateMode.valueOf(
            propertiesComponent.getValue("updateMode", UpdateMode.INTERACTIVE_MODE.name()));
  }

  @Override
  public void setSessionToken(String sessionToken) {
    super.setSessionToken(sessionToken);
    propertiesComponent.setValue("sessionToken", sessionToken);
  }

  @Override
  public void setLoginUrl(String loginUrl) {
    super.setLoginUrl(loginUrl);
    propertiesComponent.setValue("loginUrl", loginUrl);
  }

  @Override
  public void setUseLinter(boolean useLinter) {
    super.setUseLinter(useLinter);
    propertiesComponent.setValue("useLinter", useLinter);
  }

  @Override
  public void setMinSeverity(int minSeverity) {
    super.setMinSeverity(minSeverity);
    propertiesComponent.setValue("minSeverity", String.valueOf(minSeverity));
  }

  @Override
  public void setApiUrl(@NotNull String apiUrl) {
    super.setApiUrl(apiUrl);
    propertiesComponent.setValue("apiUrl", apiUrl);
  }

  @Override
  public void setEnable(boolean isEnable) {
    super.setEnable(isEnable);
    propertiesComponent.setValue("isEnable", isEnable, true);
  }

  @Override
  public boolean consentGiven(@NotNull Object projectO) {
    Project project = PDU.toProject(projectO);
    return PropertiesComponent.getInstance(project).getBoolean("consentGiven");
  }

  @Override
  public void setConsentGiven(@NotNull Object projectO) {
    Project project = PDU.toProject(projectO);
    PropertiesComponent.getInstance(project).setValue("consentGiven", true);
  }

  public boolean isFirstStart() {
    boolean result = PropertiesComponent.getInstance().getBoolean("deepcode.isFirstStart", true);
    if (result) PropertiesComponent.getInstance().setValue("deepcode.isFirstStart", false, true);
    return result;
  }

  public boolean needToShowReplacementMessage() {
    int lastMonthShown = PropertiesComponent.getInstance().getInt("deepcode.lastMonthReplacementMessageShownFor", 0);
    int currentMonth = Calendar.getInstance().get(Calendar.MONTH);
    int currentYear = Calendar.getInstance().get(Calendar.YEAR);
    final boolean result = lastMonthShown != currentMonth || (currentYear == 2021 && currentMonth >= Calendar.AUGUST);
    if (result) PropertiesComponent.getInstance().setValue("deepcode.lastMonthReplacementMessageShownFor", currentMonth, 0);
    return result;
  }

  public boolean isPluginDeprecated() {
    int currentDayOfMonth = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
    int currentMonth = Calendar.getInstance().get(Calendar.MONTH);
    int currentYear = Calendar.getInstance().get(Calendar.YEAR);
    return currentYear >= 2021 && currentMonth >= Calendar.AUGUST && currentDayOfMonth > 7;
  }

  public UpdateMode getUpdateMode() {
    return updateMode;
  }

  public void setUpdateMode(UpdateMode updateMode) {
    this.updateMode = updateMode;
    propertiesComponent.setValue("updateMode", updateMode.name());
  }
}
