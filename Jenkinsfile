pipeline{
  agent any
  tools {
    jdk 'adoptopenjdk-hotspot-jdk8-latest'
  }
  environment {
    MAVEN_HOME = "$WORKSPACE/.m2/"
    MAVEN_USER_HOME = "$MAVEN_HOME"
  }
  stages{
    stage("Maven Build"){
        steps {
          withMaven {
            sh 'mvn -B verify --file lemminx-maven/pom.xml'
          }
        }
    }
  }
}
