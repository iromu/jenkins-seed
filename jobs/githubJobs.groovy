import groovy.json.JsonSlurper

String basePath = 'github'

folder(basePath) {
    description 'Jobs for http://github.com/iromu/ for each github branch, each in its own folder.'
}

println "Creating Jobs for http://github.com/iromu/"
def OAUTHTOKEN = ""
def githubApi = new URL("https://api.github.com/users/iromu/repos?q=fork:false+language:java+language:javascript")
def projects = new JsonSlurper().parse(githubApi.newReader())

projects.each { project ->
    def jobName = project.name
    def githubName = project.full_name
    def repo = githubName
    def gitUrl = project.clone_url
    def gitLanguage = project.language
    def gitFork = project.fork
    def gitDefaultBranch = project.default_branch


    if (!gitFork) {


        folder "$basePath/$jobName"

        URL branchUrl = "https://api.github.com/repos/$githubName/branches?access_token=$OAUTHTOKEN".toURL()
        List branches = new JsonSlurper().parse(branchUrl.newReader())

        branches.each { branch ->

            String safeBranchName = branch.name.replaceAll('/', '-')
            folder "$basePath/$jobName/$safeBranchName"
            println "Creating Jobs $basePath/$safeBranchName/build for ${gitUrl}"
            final jobNamePrefix = "$basePath/$jobName/$safeBranchName/"

            switch (gitLanguage) {
                case 'Java':
                    mavenCiJobBuilder(jobNamePrefix, branch, safeBranchName, gitUrl, gitLanguage, basePath, jobName)
                    break
                case 'JavaScript':
                    job(jobNamePrefix + "build") {
                        scm {
                            git {
                                remote {
                                    url(gitUrl)
                                }
                                branch(safeBranchName)
                                createTag(false)
                                clean()
                            }
                        }
                        wrappers {
                            colorizeOutput()
                            preBuildCleanup()
                        }
                        triggers {
                            scm 'H/5 * * * *'
                            githubPush()
                        }
                        steps {
                            shell 'npm update && npm build'
                        }
                    }
                    job(jobNamePrefix + "verify") {
                        scm {
                            cloneWorkspace(jobNamePrefix + "build")
                        }
                        wrappers {
                            colorizeOutput()
                            preBuildCleanup()
                        }
                        triggers {
                            scm 'H/5 * * * *'
                            githubPush()
                        }
                        steps {
                            shell 'npm run-script ci-test'
                        }
                    }
                    break
            }
        }
    }
}

private void mavenCiJobBuilder(GString jobNamePrefix, String safeBranchName, gitUrl, gitLanguage, String basePath, jobName) {

    job(jobNamePrefix + "build") {
        scm {
            git {
                remote {
                    url(gitUrl)
                }
                branch(safeBranchName)
                createTag(false)
                clean()
            }
        }
        wrappers {
            colorizeOutput()
            preBuildCleanup()
        }
        triggers {
            scm 'H/5 * * * *'
            githubPush()
        }
        steps {
            maven {
                rootPOM('pom.xml')
                mavenInstallation('Maven 3.3.3')
                goals('clean package -fn')
                property('skipITs', 'true')
                property('maven.test.failure.ignore', 'true')
            }
        }
        publishers {
            chucknorris()
            archiveJunit('**/target/surefire-reports/*.xml')
            publishCloneWorkspace('**', '', 'Any', 'TAR', true, null)
            downstreamParameterized {
                trigger("$basePath/$jobName/$safeBranchName/verify") {

                }
            }
        }
    }

    job(jobNamePrefix + "verify") {
        scm {
            cloneWorkspace(jobNamePrefix + "build")
        }
        wrappers {
            colorizeOutput()
            preBuildCleanup()
        }
        steps {
            if (gitLanguage == 'Java') {
                maven {
                    rootPOM('pom.xml')
                    goals('clean verify')
                    mavenOpts('-Xms512m -Xmx1024m')
                    mavenInstallation('Maven 3.3.3')
                }
            }
        }
        publishers {
            archiveJunit('**/target/surefire-reports/*.xml')
            publishCloneWorkspace('**', '', 'Any', 'TAR', true, null)
            downstreamParameterized {
                trigger(jobNamePrefix + "analysis") {

                }
            }
        }
    }

    job(jobNamePrefix + "analysis") {
        scm {
            cloneWorkspace(jobNamePrefix + "verify")
        }
        wrappers {
            colorizeOutput()
            preBuildCleanup()
        }
        steps {
            maven {
                goals('org.jacoco:jacoco-maven-plugin:0.7.4.201502262128:prepare-agent install -Psonar')
                mavenInstallation('Maven 3.3.3')
                rootPOM('pom.xml')
                mavenOpts('-Xms512m -Xmx1024m')

            }
            maven {
                goals('sonar:sonar -Psonar')
                mavenInstallation('Maven 3.3.3')
                rootPOM('pom.xml')
                mavenOpts('-Xms512m -Xmx1024m')
            }
        }
        publishers {
            chucknorris()
        }
    }
}