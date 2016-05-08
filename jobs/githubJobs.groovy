import groovy.json.JsonSlurper

String basePath = 'github'

folder(basePath) {
    description 'This example shows how to create a set of jobs for each github branch, each in its own folder.'
}

println "Creating Jobs for https://api.github.com/users/iromu/repos"
def OAUTHTOKEN = ""
//def githubApi = new URL("https://api.github.com/users/iromu/repos?access_token=$OAUTHTOKEN")
def githubApi = new URL("https://api.github.com/users/iromu/repos")
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

        //URL branchUrl = "https://api.github.com/repos/$githubName/branches?access_token=$OAUTHTOKEN".toURL()
        URL branchUrl = "https://api.github.com/repos/$githubName/branches".toURL()
        List branches = new JsonSlurper().parse(branchUrl.newReader())

        branches.each { branch ->

            if (gitLanguage == 'Java') {
                String safeBranchName = branch.name.replaceAll('/', '-')
                folder "$basePath/$jobName/$safeBranchName"
                println "Creating Jobs $basePath/$safeBranchName/build for ${gitUrl}"
                final jobNamePrefix = "$basePath/$jobName/$safeBranchName/"

                createJavaJobs(jobNamePrefix, branch, safeBranchName, gitUrl, gitLanguage, basePath, jobName)
            }
        }
    }
}

private void createJavaJobs(GString jobNamePrefix, gitbranch, String safeBranchName, gitUrl, gitLanguage, String basePath, jobName) {

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
            cloneWorkspace("$basePath/$jobName/$safeBranchName/build")
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
                trigger("$basePath/$jobName/$safeBranchName/analysis") {

                }
            }
        }
    }

    job(jobNamePrefix + "analysis") {
        scm {
            cloneWorkspace("$basePath/$jobName/$safeBranchName/verify")
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