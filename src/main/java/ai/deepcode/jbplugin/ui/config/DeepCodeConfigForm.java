package ai.deepcode.jbplugin.ui.config;

import ai.deepcode.jbplugin.core.DeepCodeParams;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class DeepCodeConfigForm {
  private JPanel rootPanel;
  private JTextField baseURL;
  private JTextField tokenID;
  private JCheckBox addLinters;
  private JComboBox minSeverityLevel;
  private JCheckBox enableDeepCodePlugin;
  private JComboBox updateMode;

  public DeepCodeConfigForm() {
    minSeverityLevel.setModel(
        new DefaultComboBoxModel(
            new String[] {"Infos, Warnings and Errors", "Warnings and Errors", "Errors only"}));
    updateMode.setModel(
        new DefaultComboBoxModel(
            new String[] {
              "Interactive (on any source file change)",
              "On Save (when source file saved on disk)",
              "On Demand (only if explicitly invoked)"
            }));
    updateMode.setSelectedIndex(1);
    minSeverityLevel.setSelectedIndex(0);
  }

  public JPanel getRoot() {
    return rootPanel;
  }

  @NotNull
  public String getBaseURL() {
    return baseURL.getText();
  }

  public void setBaseURL(String baseURL) {
    this.baseURL.setText(baseURL);
  }

  public String getTokenID() {
    return tokenID.getText();
  }

  public void setTokenID(String tokenID) {
    this.tokenID.setText(tokenID);
  }

  public boolean isLintersEnabled() {
    return addLinters.getModel().isSelected();
  }

  public void setAddLinters(boolean addLinters) {
    this.addLinters.getModel().setSelected(addLinters);
  }

  public int getMinSeverityLevel() {
    return minSeverityLevel.getSelectedIndex() + 1;
  }

  public void setMinSeverityLevel(int minSeverityLevel) {
    this.minSeverityLevel.setSelectedIndex(minSeverityLevel - 1);
  }

  public DeepCodeParams.UpdateMode getUpdateMode() {
    return DeepCodeParams.UpdateMode.values()[updateMode.getSelectedIndex()];
  }

  public void setUpdateMode(DeepCodeParams.UpdateMode mode) {
    this.updateMode.setSelectedIndex(mode.ordinal());
  }

  public boolean isPluginEnabled() {
    return enableDeepCodePlugin.getModel().isSelected();
  }

  public void enablePlugin(boolean enableDeepCodePlugin) {
    this.enableDeepCodePlugin.getModel().setSelected(enableDeepCodePlugin);
  }
}
