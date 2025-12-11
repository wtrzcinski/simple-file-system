Simple in-memory file system
----------------------------

[![license](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](https://www.apache.org/licenses/LICENSE-2.0)

Simple in-memory file system for Java 22 and above that
- implements the [java.nio.file](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/nio/file/package-summary.html) file system APIs
- utilizes the [java.lang.foreign](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/foreign/package-summary.html) memory access APIs

Get started
-----------

```kotlin
// todo wojtek
implementation("org.jetbrains.kotlin:kotlin-reflect:2.2.21")
```

Usage
-----

```kotlin
val givenDirectoryPath = pathProvider.getPath(newAlphanumericString(10))
val givenFilePath = givenDirectoryPath.resolve(newAlphanumericString(10))
val givenFileContent = newAlphanumericString(512)

Files.createDirectory(givenDirectoryPath)
Files.writeString(givenFilePath, givenFileContent, Charsets.UTF_8)

assertThat(Files.exists(givenDirectoryPath)).isTrue()
assertThat(Files.exists(givenFilePath)).isTrue()
assertThat(Files.readString(givenFilePath)).isEqualTo(givenFileContent)
```

Build
-----

```
./gradlew clean check
```

License
-------

```
Copyright 2025 Wojciech Trzciński

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
