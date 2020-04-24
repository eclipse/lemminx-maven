pipeline{
  environment {
      USER = "jenkins"
      MAVEN_HOME = "$WORKSPACE/.m2/"
      MAVEN_USER_HOME = "$MAVEN_HOME"
  }
  agent {
    kubernetes {
      label 'my-agent-pod-6'
      yaml """
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: maven
    image: maven:3.6.3-jdk-11
    tty: true
    command:
    - cat
    resources:
      limits:
        memory: "4Gi"
        cpu: "2000m"
      requests:
        memory: "4Gi"
        cpu: "1000m"
"""
    }
  }
  stages{
	stage("View Maven infos") {
		steps {
			container('maven') {
				sh 'echo "Effective settings" && mvn -f lemminx-maven/pom.xml help:effective-settings -Dmaven.repo.local=$WORKSPACE/.m2/repository'
				sh 'echo "Effective pom" && mvn -f lemminx-maven/pom.xml help:effective-pom -Dmaven.repo.local=$WORKSPACE/.m2/repository'
				sh '''
					export settings_localRepository=$(mvn -f lemminx-maven/pom.xml help:evaluate -Dexpression=settings.localRepository  -q -DforceStdout -Dmaven.repo.local=$WORKSPACE/.m2/repository)
					echo "settings.localRepository=${settings_localRepository}"
					echo "ls surefire..."
					ls -l ${settings_localRepository}/org/apache/maven/plugins/maven-surefire-plugin/*
				'''
			}
		}
	}
	stage("Maven Build"){
		steps {
			container('maven') {
				sh 'mvn -B verify --file lemminx-maven/pom.xml -Dmaven.test.error.ignore=true -Dmaven.test.failure.ignore=true -Dmaven.repo.local=$WORKSPACE/.m2/repository'    
			}
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
          sh 'mvn deploy -B -DskipTests -Dcbi.jarsigner.skip=false --file lemminx-maven/pom.xml -Dmaven.repo.local=$WORKSPACE/.m2/repository'
        }
      }
    }
  }
}
