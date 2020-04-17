package ai.deepcode.jbplugin.ui.config;

import ai.deepcode.jbplugin.DeepCodeNotifications;
import ai.deepcode.jbplugin.utils.DeepCodeParams;
import ai.deepcode.jbplugin.utils.DeepCodeUtils;
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
        && (!myForm.getBaseURL().equals(DeepCodeParams.getApiUrl())
            || !myForm.getTokenID().equals(DeepCodeParams.getSessionToken())
            || !(myForm.isPluginEnabled() == DeepCodeParams.isEnable())
            || !(myForm.isLintersEnabled() == DeepCodeParams.useLinter())
            || myForm.getMinSeverityLevel() != DeepCodeParams.getMinSeverity());
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
    DeepCodeParams.setSessionToken(myForm.getTokenID());
    DeepCodeParams.setMinSeverity(myForm.getMinSeverityLevel());
    DeepCodeParams.setUseLinter(myForm.isLintersEnabled());
    DeepCodeParams.setEnable(myForm.isPluginEnabled());
    DeepCodeParams.setApiUrl(myForm.getBaseURL());
    // Initiate new Login if needed
    if (DeepCodeUtils.isNotLogged(null)) {
//      DeepCodeNotifications.reShowLastNotification();
    }
  }

  @Override
  public void reset() {
    if (myForm == null) return;
    myForm.setBaseURL(DeepCodeParams.getApiUrl());
    myForm.setTokenID(DeepCodeParams.getSessionToken());
    myForm.setAddLinters(DeepCodeParams.useLinter());
    myForm.setMinSeverityLevel(DeepCodeParams.getMinSeverity());
    myForm.enablePlugin(DeepCodeParams.isEnable());
  }
}
