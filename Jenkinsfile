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
            sh './mvnw -B verify --file lemminx-maven/pom.xml -DskipTests'
          }
        }
    }
    stage ('Deploy Maven artifacts') {
      when {
          branch 'master'
      }
      steps {
        withMaven {
          sh './mvnw clean deploy -B -DskipTests -Dcbi.jarsigner.skip=false --file lemminx-maven/pom.xml'
        }
      }
    }
  }
}
