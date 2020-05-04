/*
 * This file is part of the gradle-release plugin.
 *
 * (c) Eric Berry
 * (c) ResearchGate GmbH
 *
 * For the full copyright and license information, please view the LICENSE
 * file that was distributed with this source code.
 */

package net.researchgate.release

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.util.ConfigureUtil

import java.util.regex.Matcher
import java.util.regex.Pattern

import net.researchgate.release.GitAdapter.GitConfig
import net.researchgate.release.SvnAdapter.SvnConfig

class ReleaseExtension {

    boolean failOnCommitNeeded = true

    boolean failOnPublishNeeded = true

    boolean failOnSnapshotDependencies = true

    boolean failOnUnversionedFiles = true

    boolean failOnUpdateNeeded = true

    boolean revertOnFail = true

    String preCommitText = ''

    String preTagCommitMessage = '[Gradle Release Plugin] - pre tag commit: '

    String tagCommitMessage = '[Gradle Release Plugin] - creating tag: '

    String newVersionCommitMessage = '[Gradle Release Plugin] - new version commit: '

    String snapshotSuffix = '-SNAPSHOT'

    def pushReleaseVersionBranch = false

    /**
     * as of 3.0 set this to "$version" by default
     */
    String tagTemplate

    String versionPropertyFile = 'gradle.properties'

    List versionProperties = []

    List buildTasks = ['build']

    List ignoredSnapshotDependencies = []

    Map<String, Closure<String>> versionPatterns = [
        // Increments last number: "2.5-SNAPSHOT" => "2.6-SNAPSHOT"
        /(\d+)([^\d]*$)/: { Matcher m, Project p -> m.replaceAll("${(m[0][1] as int) + 1}${m[0][2]}") }
    ]

    SvnConfig svn = new SvnConfig()
    GitConfig git = new GitConfig()

    def git(Action<GitConfig> action) {
        action.execute(git)
    }

    def svn(Action<SvnConfig> action) {
        action.execute(svn)
    }

    List<Class<? extends BaseScmAdapter>> scmAdapters = [
        GitAdapter,
        SvnAdapter,
        HgAdapter,
        BzrAdapter,
    ]

    private Project project
    private Map<String, Object> attributes

    ReleaseExtension(Project project, Map<String, Object> attributes) {
        this.attributes = attributes
        this.project = project
        ExpandoMetaClass mc = new ExpandoMetaClass(ReleaseExtension, false, true)
        mc.initialize()
        metaClass = mc
    }

    def propertyMissing(String name) {
        if (isDeprecatedOption(name)) {
            def value = null
            if (name == 'includeProjectNameInTag') {
                value = false
            }

            return metaClass."$name" = value
        }
        throw new MissingPropertyException(name, this.class)
    }

    def propertyMissing(String name, value) {
        if (isDeprecatedOption(name)) {
            project.logger?.warn("You are setting the deprecated option '${name}'. The deprecated option will be removed in 3.0")
            project.logger?.warn("Please upgrade your configuration to use 'tagTemplate'. See https://github.com/researchgate/gradle-release/blob/master/UPGRADE.md#migrate-to-new-tagtemplate-configuration")

            return metaClass."$name" = value
        }
    }

    def methodMissing(String name, args) {
        // adds a method for SCM adapter. Just in case if there is another call for the same adapter from another place
        metaClass."$name" = { Closure varClosure ->
            return ConfigureUtil.configure(varClosure, this."$name")
        }

        def scmConfig
        try {
            // get or create ScmConfig (field accessor delegates to propertyMissing(String) method if needed)
            scmConfig = this."$name"
        } catch (MissingPropertyException ignored) {
            throw new MissingMethodException(name, this.class, args)
        }
        return ConfigureUtil.configure(args[0] as Closure, scmConfig)
    }

    private boolean isDeprecatedOption(String name) {
        name == 'includeProjectNameInTag' || name == 'tagPrefix'
    }
}
