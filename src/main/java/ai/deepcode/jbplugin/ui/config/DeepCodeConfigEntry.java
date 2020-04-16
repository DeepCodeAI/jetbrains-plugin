package ai.deepcode.jbplugin.ui.config;

import ai.deepcode.jbplugin.utils.DeepCodeParams;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class DeepCodeConfigEntry implements Configurable {
  private DeepCodeConfigForm myForm;


  @Override
  public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
    return "DeepCode Settings";
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
    return (myForm != null) && (!DeepCodeParams.getApiUrl().equals(myForm.getBaseURL()));
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
    DeepCodeParams.setApiUrl(myForm.getBaseURL());
  }

  @Override
  public void reset() {
    if (myForm == null) return;
    myForm.setBaseURL(DeepCodeParams.getApiUrl());
  }
}
