package ai.deepcode.jbplugin.ui.config;

import ai.deepcode.jbplugin.DeepCodeNotifications;
import ai.deepcode.jbplugin.core.*;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class DeepCodeConfigEntry implements Configurable {
  private DeepCodeConfigForm myForm;

  // Default values
  private final String defaultBaseUrl = "https://www.deepcode.ai/";
  private final String defaultTokenId = "";

  @Override
  public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
    return "DeepCode Settings";
  }

  @Override
  public @Nullable String getHelpTopic() {
    return "Configuration for the DeepCode plugin";
  }

  @Override
  public @Nullable JComponent createComponent() {
    myForm = new DeepCodeConfigForm();
    reset();
    return myForm.getRoot();
  }

  /**
   * Indicates whether the Swing form was modified or not. This method is called very often, so it
   * should not take a long time.
   *
   * @return {@code true} if the settings were modified, {@code false} otherwise
   */
  @Override
  public boolean isModified() {
    return (myForm != null)
        && (!myForm.getBaseURL().equals(DeepCodeParams.getInstance().getApiUrl())
            || !myForm.getTokenID().equals(DeepCodeParams.getInstance().getSessionToken())
            || !(myForm.isPluginEnabled() == DeepCodeParams.getInstance().isEnable())
            || !(myForm.isLintersEnabled() == DeepCodeParams.getInstance().useLinter())
            || myForm.getMinSeverityLevel() != DeepCodeParams.getInstance().getMinSeverity());
  }

  @Override
  public void disposeUIResources() {
    myForm = null;
  }

  /**
   * Stores the settings from the Swing form to the configurable component. This method is called on
   * EDT upon user's request.
   *
   * @throws ConfigurationException if values cannot be applied
   */
  @Override
  public void apply() throws ConfigurationException {
    if (myForm == null) return;
    boolean tokenChanged, urlChanged, severityChanged, linterChanged, enableChanged = false;
    if (tokenChanged =
        !myForm.getTokenID().equals(DeepCodeParams.getInstance().getSessionToken())) {
      DeepCodeParams.getInstance().setSessionToken(myForm.getTokenID());
      DeepCodeParams.getInstance().setLoginUrl("");
    }
    if (urlChanged = !myForm.getBaseURL().equals(DeepCodeParams.getInstance().getApiUrl())) {
      DeepCodeParams.getInstance().setApiUrl(myForm.getBaseURL());
    }
    if (severityChanged =
        myForm.getMinSeverityLevel() != DeepCodeParams.getInstance().getMinSeverity()) {
      DeepCodeParams.getInstance().setMinSeverity(myForm.getMinSeverityLevel());
    }
    if (linterChanged = myForm.isLintersEnabled() != DeepCodeParams.getInstance().useLinter()) {
      DeepCodeParams.getInstance().setUseLinter(myForm.isLintersEnabled());
    }
    if (enableChanged = myForm.isPluginEnabled() != DeepCodeParams.getInstance().isEnable()) {
      DeepCodeParams.getInstance().setEnable(myForm.isPluginEnabled());
    }
    if (tokenChanged || urlChanged || severityChanged || linterChanged || enableChanged) {
      AnalysisData.getInstance().resetCachesAndTasks(null);
      DeepCodeNotifications.expireShownLoginNotifications();
      if (LoginUtils.getInstance().isLogged(null, true)) {
        RunUtils.getInstance().asyncAnalyseProjectAndUpdatePanel(null);
      }
    }
  }

  @Override
  public void reset() {
    if (myForm == null) return;
    myForm.setBaseURL(DeepCodeParams.getInstance().getApiUrl());
    myForm.setTokenID(DeepCodeParams.getInstance().getSessionToken());
    myForm.setAddLinters(DeepCodeParams.getInstance().useLinter());
    myForm.setMinSeverityLevel(DeepCodeParams.getInstance().getMinSeverity());
    myForm.enablePlugin(DeepCodeParams.getInstance().isEnable());
  }
}
