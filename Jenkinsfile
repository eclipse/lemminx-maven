pipeline{
  agent any
  tools {
    jdk 'adoptopenjdk-hotspot-jdk8-latest'
    maven 'apache-maven-latest'
  }
  stages{
    stage("Maven Build"){
        steps {
            sh 'mvn -B verify --file lemminx-maven/pom.xml -Dmaven.test.error.ignore=true -Dmaven.test.failure.ignore=true'
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
