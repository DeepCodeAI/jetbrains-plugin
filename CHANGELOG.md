## [1.2.8] - 2019-06-20
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
