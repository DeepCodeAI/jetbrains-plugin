### Build

- Clone this repository: `git clone https://github.com/DeepCodeAI/jetbrains-plugin.git` 
- Switch to the dev branch: `git checkout origin/dev`
- Install Java 11 or higher (https://adoptopenjdk.net/) and/or make sure JAVA_HOME is pointing to Java 11 installation path of the JDK;
- Check if java is correctly installed (and your java version) with `java -version` command;
- Place `java-client-{X.X.X}-all.jar` into `..\libs` dir (see [java-client](https://github.com/DeepCodeAI/java-client) repository for instruction how to build it);
- At `build.gradle` file inside `intellij` block: uncomment `version` line and comment `localPath` line (or change `localPath` to pointing your locally installed Intellij Idea `2019.2` version instance);
- Run `gradlew buildPlugin`
- Look for resulting ZIP file at `..\build\distributions`

### Useful links
- IntelliJ Platform SDK [documentation](https://www.jetbrains.org/intellij/sdk/docs/intro/welcome.html)
- JetBrains Marketplace [documentation](https://plugins.jetbrains.com/docs/marketplace/about-marketplace.html)
- [Community SDK Forum](https://intellij-support.jetbrains.com/hc/en-us/community/topics/200366979-IntelliJ-IDEA-Open-API-and-Plugin-Development)
