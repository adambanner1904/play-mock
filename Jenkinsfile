pipeline {
    agent { docker { image 'maven:3.9.16-eclipse-temurin-21-alpine' } }
    stages {
        stage('build') {
            steps {
                sbt 'clean compile test'
            }
        }
    }
}
