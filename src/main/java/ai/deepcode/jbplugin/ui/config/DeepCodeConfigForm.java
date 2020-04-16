package ai.deepcode.jbplugin.ui.config;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class DeepCodeConfigForm {
  private JPanel rootPanel;
  private JTextField baseURL;
  private JTextField tokenID;
  private JCheckBox addLinters;
  private JComboBox minSeverityLevel;
  private JCheckBox enableDeepCodePluginCheckBox;

  public DeepCodeConfigForm() {
    minSeverityLevel.setModel(
        new DefaultComboBoxModel(
            new String[] {"Infos, Warnings and Errors", "Warnings and Errors", "Errors only"}));
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
}
