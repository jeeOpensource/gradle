/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.plugins.ide.idea
import org.gradle.integtests.fixtures.TestResources
import org.gradle.plugins.ide.AbstractIdeIntegrationSpec
import org.junit.Rule

class IdeaJavaLanguageSettingsIntegrationTest extends AbstractIdeIntegrationSpec {
    @Rule
    public final TestResources testResources = new TestResources(testDirectoryProvider)

    def setup() {
        settingsFile << """
rootProject.name = 'root'
include ':child1', ':child2', ':child3'
"""
    }

    void "global sourceCompatibility results in project language level"() {
        given:
        buildFile << """
allprojects {
    apply plugin:'idea'
    apply plugin:'java'

    sourceCompatibility = "1.7"
}
"""
        when:
        succeeds "idea"

        then:
        ipr.languageLevel == "JDK_1_7"
        iml('root').languageLevel == null
        iml('child1').languageLevel == null
        iml('child2').languageLevel == null
        iml('child3').languageLevel == null
    }

    void "specific module languagelevel is exposed with derived language level"() {
        given:
        buildFile << """
allprojects {
    apply plugin:'idea'
    apply plugin:'java'

    sourceCompatibility = 1.6
}

project(':child1') {
    sourceCompatibility = 1.7
}

project(':child2') {
    sourceCompatibility = 1.5
}

project(':child3') {
    sourceCompatibility = 1.8
}
"""
        when:
        succeeds "idea"

        then:
        ipr.languageLevel == "JDK_1_8"
        iml('root').languageLevel == "JDK_1_6"
        iml("child1").languageLevel == "JDK_1_7"
        iml("child2").languageLevel == "JDK_1_5"
        iml("child3").languageLevel == null
    }

    void "use project language level when explicitly set"() {
        given:
        buildFile << """
allprojects {
    apply plugin:'idea'
    apply plugin:'java'

    sourceCompatibility = 1.4
}

idea {
    project {
        jdkName   = 1.8
        languageLevel = 1.7
    }
}

project(':child1') {
    sourceCompatibility = 1.6
}

project(':child2') {
    sourceCompatibility = 1.5
}

project(':child3') {
    sourceCompatibility = 1.8
}
"""
        when:
        succeeds "idea"

        then:
        ipr.languageLevel == "JDK_1_7"
        ipr.jdkName == "1.8"
        iml('root').languageLevel == null
        iml('child1').languageLevel == null
        iml('child2').languageLevel == null
        iml('child3').languageLevel == null
    }

    void "uses subproject sourceCompatibility even if root project does not apply java plugin"() {
        buildFile << """
allprojects {
    apply plugin: 'idea'
}
subprojects {
    apply plugin:'java'
    sourceCompatibility = 1.7
}
"""

        when:
        succeeds "idea"

        then:
        ipr.languageLevel == "JDK_1_7"
        iml('child1').languageLevel == null
        iml('child2').languageLevel == null
        iml('child3').languageLevel == null
    }

    void "module languagelevel always exposed when no idea root project found"() {
        buildFile << """
subprojects {
    apply plugin:'java'
    apply plugin: 'idea'
    sourceCompatibility = 1.7
}
"""

        when:
        succeeds "idea"

        then:
        iml('child1').languageLevel == "JDK_1_7"
        iml('child2').languageLevel == "JDK_1_7"
        iml('child3').languageLevel == "JDK_1_7"
    }


    def "no explicit bytecodeLevel for same java versions"() {
        given:
        settingsFile << """
rootProject.name = "root"
include 'subprojectA'
include 'subprojectB'
include 'subprojectC'
"""

        def buildFile = file("build.gradle")
        buildFile << """
allprojects {
    apply plugin: 'java'
    apply plugin: 'idea'
    targetCompatibility = '1.6'
}

idea {
    project {
        jdkName = "1.6"
    }
}

"""

        when:
        executer.usingBuildScript(buildFile).usingSettingsFile(settingsFile).withTasks("idea").run()

        then:
        assert ipr.bytecodeTargetLevel.size() == 0
    }

    def "explicit project target level when module version differs from project java sdk"() {
        given:
        settingsFile << """
rootProject.name = "root"
include 'subprojectA'
include 'subprojectB'
include 'subprojectC'
"""

        def buildFile = file("build.gradle")
        buildFile << """
allprojects {
    apply plugin: 'java'
    apply plugin: 'idea'
    targetCompatibility = '1.7'
}

idea {
    project {
        jdkName = "1.8"
    }
}
"""

        when:
        executer.usingBuildScript(buildFile).usingSettingsFile(settingsFile).withTasks("idea").run()

        then:
        assert ipr.bytecodeTargetLevel.size() == 1
        assert ipr.bytecodeTargetLevel.@target == "1.7"
    }

    def "can have module specific bytecode version"() {
        given:
        settingsFile << """
rootProject.name = "root"
include 'subprojectA'
include 'subprojectB'
include 'subprojectC'
include 'subprojectD'
"""

        def buildFile = file("build.gradle")
        buildFile << """
configure(project(':subprojectA')) {
    apply plugin: 'java'
    apply plugin: 'idea'
    targetCompatibility = '1.6'
}

configure(project(':subprojectB')) {
    apply plugin: 'java'
    apply plugin: 'idea'
    targetCompatibility = '1.7'
}

configure(project(':subprojectC')) {
    apply plugin: 'java'
    apply plugin: 'idea'
    targetCompatibility = '1.8'
}

configure(project(':subprojectD')) {
    apply plugin: 'idea'
}

apply plugin:'idea'
idea {
    project {
        jdkName = "1.8"
    }
}

"""

        when:
        executer.usingBuildScript(buildFile).usingSettingsFile(settingsFile).withTasks("idea").run()

        then:
        assert ipr.bytecodeTargetLevel.size() == 1
        assert ipr.bytecodeTargetLevel.module.size() == 2
        assert ipr.bytecodeTargetLevel.module.find { it.@name == "subprojectA" }.@target == "1.6"
        assert ipr.bytecodeTargetLevel.module.find { it.@name == "subprojectB" }.@target == "1.7"
    }

    def getIpr() {
        return parseIpr("root.ipr")
    }

    def iml(String name = 'root') {
        if (name == 'root') {
            return parseIml('root.iml')
        }
        return parseIml("${name}/${name}.iml")
    }

    protected IdeaProjectFixture parseIpr(Map options = [:], String projectFile) {
        return new IdeaProjectFixture(parseFile(options, projectFile))
    }
}
