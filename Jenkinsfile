pipeline{
  agent any
  tools {
    jdk 'adoptopenjdk-hotspot-jdk8-latest'
    maven 'apache-maven-latest'
  }
  stages{
    stage("View Maven infos") {
		steps {
			sh 'echo "Effective settings" && mvn -f lemminx-maven/pom.xml help:effective-settings'
			sh 'echo "Effective pom" && mvn -f lemminx-maven/pom.xml help:effective-pom'
			sh '''
				export settings_localRepository=$(mvn -f lemminx-maven/pom.xml help:evaluate -Dexpression=settings.localRepository  -q -DforceStdout)
				echo "settings.localRepository=${settings_localRepository}"
				echo "ls surefire..."
				ls -l ${settings_localRepository}/org/apache/maven/plugins/maven-surefire-plugin/*
			'''
		}
    }
    stage("Maven Build"){
        steps {
            sh 'mvn -X -B verify --file lemminx-maven/pom.xml -Dmaven.test.error.ignore=true -Dmaven.test.failure.ignore=true'
        }
        post {
			always {
				junit 'lemminx-maven/target/surefire-reports/TEST-*.xml'
				archiveArtifacts artifacts: 'lemminx-maven/target/*.jar'
			}
		}
    }
    stage ('Deploy Maven artifacts') {
      when {
          branch 'master'
      }
      steps {
        withMaven {
          sh 'mvn deploy -B -DskipTests -Dcbi.jarsigner.skip=false --file lemminx-maven/pom.xml'
        }
      }
    }
  }
}
