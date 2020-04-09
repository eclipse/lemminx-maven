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
    stage('Deploy to downloads.eclipse.org') {
      when {
        branch 'master'
      }
      steps {
        sshagent ( ['projects-storage.eclipse.org-bot-ssh']) {
          sh '''
            targetDir=/home/data/httpd/download.eclipse.org/lemminx/lemminx-maven/snapshots
            ssh genie.lemminx@projects-storage.eclipse.org rm -rf $targetDir
            ssh genie.lemminx@projects-storage.eclipse.org mkdir -p $targetDir
            scp -r lemminx-maven/lemminx-maven/target/lemminx-maven-* genie.lemminx@projects-storage.eclipse.org:$targetDir
            '''
        }
      }
    }
  }
}

