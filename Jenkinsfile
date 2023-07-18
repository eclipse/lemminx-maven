pipeline{
	agent any
	tools {
		maven 'apache-maven-latest'
		jdk 'openjdk-jdk17-latest'
	}
	environment {
		MAVEN_HOME = "$WORKSPACE/.m2/"
		MAVEN_USER_HOME = "$MAVEN_HOME"
	}
	stages{
		stage("Maven Build"){
			steps {
				sh 'mvn -B verify --file lemminx-maven/pom.xml -Pgenerate-vscode-jars -Dmaven.test.error.ignore=true -Dmaven.test.failure.ignore=true'
			}
			post {
				always {
					junit 'lemminx-maven/target/surefire-reports/TEST-*.xml'
					archiveArtifacts 'lemminx-maven/src/test/resources/build.log'
				}
			}
		}
		stage ('Deploy Maven artifacts') {
			when {
				branch 'master'
			}
			steps {
				sh 'mvn -B deploy  --file lemminx-maven/pom.xml -DskipTests -Dcbi.jarsigner.skip=false'
			}
		}
	}
}
