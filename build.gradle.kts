/**
 * Copyright 2025 Wojciech Trzciński
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
    kotlin("jvm") version "2.2.21"
}

val javaVersion = 24
group = "org.wtrzcinski.files"
version = "2025.12.01"

plugins.withType<JavaPlugin> {
    the<JavaPluginExtension>().apply {
        toolchain {
            languageVersion = JavaLanguageVersion.of(javaVersion)
        }
    }
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "$javaVersion"
    targetCompatibility = "$javaVersion"
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.2.21")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:2.2.21")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("org.assertj:assertj-core:3.27.6")
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.junit.platform.suite)
    testRuntimeOnly(libs.junit.platform.launcher)
}

