# Latest update 🗞️ (start here first 👇)

- Dear developers 👋, thank you for your support and feedback!
- With the DeepCode acquisition by [Snyk](https://snyk.io) we will be starting a new journey, a better one, towards helping you write robust and secure application code. The DeepCode plugin will be replaced by [Snyk's JetBrains plugin](https://plugins.jetbrains.com/plugin/10972-snyk-vulnerability-scanner) with includes DeepCode's functionality and more.
- If you want to read more about it, here is the [official announcement](https://www.deepcode.ai/). We will start sunsetting the official DeepCode API in August, 2021. In the mean time we will do one last update of the JetBrains plugin with this message to ensure you are up to date.
- We invite you to join us and start using the new Snyk plugin! We are excited and looking forward to helping you build stable and secure applications 🚀

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
| 1.2.13           | 2.0.18      |

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
