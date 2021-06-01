# Latest update 🗞️ (start here first 👇)

- Dear developers 👋, thank you for your support and feedback!
- With the DeepCode acquisition by [Snyk](https://snyk.io) we will be starting a new journey, a better one, towards helping you write robust and secure application code. The DeepCode plugin will be replaced by [Snyk's JetBrains plugin](https://plugins.jetbrains.com/plugin/10972-snyk-vulnerability-scanner) with includes DeepCode's functionality and more.
- If you want to read more about it, here is the [official announcement](https://www.deepcode.ai/). We will start sunsetting the official DeepCode API in August, 2021. In the mean time we will do one last update of the JetBrains plugin with this message to ensure you are up to date.
- We invite you to join us and start using the new Snyk plugin! We are excited and looking forward to helping you build stable and secure applications 🚀

## [1.2.14] - 2021-06
1.2.14 - fix deprecation deadline and links at plugin replacement announcement;<br>

## [1.2.13] - 2021-05
1.2.13 - 2021.1 compatibility and plugin replacement announcement;<br>

## [1.2.12] - 2019-10-05
1.2.12 - Upcoming 2020.3 compatibility and bug fixes;

## [1.2.11] - 2019-09-16
1.2.11 - Fix bug with IndexOutOfBound Exception for wrongly positioned suggestion/marker;
       - Fix bug with multiple analyses processes on project opening.

## [1.2.10] - 2019-09-14
- Update modes added:
  * Interactive (on any source file change);
  * On Save (when source file saved on disk);
  * On Demand (only if explicitly invoked).

## [1.2.9] - 2019-09-08
- Status Bar widget with summary info added; 
- .dcignore parsing fixed.

## [1.2.8] - 2019-09-03
- Improvements:
  * markers for each suggestion are shown in the List and highlighted in Preview
  * more informative Progress indicator messages

- Bug fixes:
  * fix missing re-analyse in some corner cases
  * fix missing/"too many" login/consent requests
  * fix ignoring action (quick fix) been broken in some cases
  * fix logger to not pollute IntelliJ general log (see readme.md how to collect log information)

## [1.2.0] - 2019-06-20
- Moving common logic into java-client

## [1.1.2] - 2019-06-19
- Fix bug parsing of trailing "**" in .dcignore

## [1.1.1] - 2019-06-16
- Speedup of large projects analysis and suggestions highlighting; various internal fixes and refactoring

## [1.1.0] - 2019-06-01
- Speedup of large projects analysis and suggestions highlighting
- Various internal fixes and refactoring

## [1.0.3] - 2019-05-28
- Fix multiple rescan requests and add ignoring events while rescan running
- Fix cache invalidation in Bulk mode for files update
- CheckBundle after uploadFiles to ensure no missingFiles left

## [1.0.2] - 2019-05-25
- Bugfixing and better support for refactoring

## [1.0.1] - 2019-05-22
- Updated Java-client and added support for Java 8, required for Android Studio

## [1.0.0] - 2019-05-21
### Added
- First public beta release
