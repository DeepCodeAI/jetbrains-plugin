### Build

- Clone this repository: `git clone https://github.com/DeepCodeAI/jetbrains-plugin.git` 
- Switch to the dev branch: `git checkout origin/dev`
- Install Java 11 or higher (https://adoptopenjdk.net/) and/or make sure JAVA_HOME is pointing to Java 11 installation path of the JDK;
- Check if java is correctly installed (and your java version) with `java -version` command;
- Place `java-client-{X.X.X}-all.jar` into `..\libs` dir (see [java-client](https://github.com/DeepCodeAI/java-client) repository for instruction how to build it);

**Important note: Starting version 1.2.0 use of java-client version 2.0 or above required!**

See below correspondent `java-client` version requirements:

| jetbrains-plugin | java-client |
|------------------|-------------|
| 1.2.0            | 2.0.0       |
| 1.2.2            | 2.0.4       |
| 1.2.3            | 2.0.5       |
| 1.2.4            | 2.0.6       |
| 1.2.5            | 2.0.8       |
| 1.2.7            | 2.0.12      |
| 1.2.8            | 2.0.14      |
| 1.2.10           | 2.0.16      |
| 1.2.11           | 2.0.17      |
| 1.2.12           | 2.0.18      |

**Important note: For backward compatibility build MUST be run against Intellij Idea 2019.2 instance!**
- Run `source gradlew buildPlugin`
- Look for resulting ZIP file at `..\build\distributions`

### Run tests

- environment variable with __already logged__ Token need to be declared:

`DEEPCODE_API_KEY` - logged at https://www.deepcode.ai Token 

- Run gradle test task: `source gradlew test --stacktrace --scan`

### See log output 

To see plugin's log output in idea.log (related to testing Idea instance), please enable Debug level logging 
(in testing Idea instance) by open `Help -> Diagnostic Tools -> Debug Log Settings...` and type there `DeepCode` (case-sensitive!).

### Useful links
- IntelliJ Platform SDK [documentation](https://www.jetbrains.org/intellij/sdk/docs/intro/welcome.html)
- JetBrains Marketplace [documentation](https://plugins.jetbrains.com/docs/marketplace/about-marketplace.html)
- [Community SDK Forum](https://intellij-support.jetbrains.com/hc/en-us/community/topics/200366979-IntelliJ-IDEA-Open-API-and-Plugin-Development)
