job('seed') {
    scm {
        git {
            remote {
                url('https://github.com/iromu/jenkins-seed.git')
            }
            createTag(false)
            clean()
        }
    }
    triggers {
        scm 'H/5 * * * *'
    }
    steps {
        dsl {
            external 'jobs/**/*Jobs.groovy'
            additionalClasspath 'src/main/groovy'
            removeAction('DELETE')
        }
    }
}