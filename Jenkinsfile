pipeline{
	agent {
		kubernetes {
			label 'lemminx-maven-pod3'
			defaultContainer 'maven-with-settings'
			// We use a pod with alpine Maven because we don't want the local
			// filesystem cache for maven deps, which causes issues later in
			// tests.
			// This is actually a limitation of Lemminx Maven not propertly 
			// supporting local filesystem repos of whatever configuration used
			// by default jnlp. When Lemmin Maven supports that, let's get back
			// to "agent any" which saves a lot of downloads.
			yaml """
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: maven-without-settings
    image: maven
    imagePullPolicy: Always
    tty: true
    resources:
      limits:
        memory: "4Gi"
        cpu: "2000m"
      requests:
        memory: "4Gi"
        cpu: "1000m"
    command:
    - cat
  - name: maven-with-settings
    image: maven:alpine
    tty: true
    command:
    - cat
    volumeMounts:
    - name: settings-xml
      mountPath: /home/jenkins/.m2/settings.xml
      subPath: settings.xml
      readOnly: true
    - name: settings-security-xml
      mountPath: /home/jenkins/.m2/settings-security.xml
      subPath: settings-security.xml
      readOnly: true
  volumes:
  - name: settings-xml
    secret:
      secretName: m2-secret-dir
      items:
      - key: settings.xml
        path: settings.xml
  - name: settings-security-xml
    secret:
      secretName: m2-secret-dir
      items:
      - key: settings-security.xml
        path: settings-security.xml
"""
		}
	}
  environment {
    MAVEN_HOME = "$WORKSPACE/.m2/"
    MAVEN_USER_HOME = "$MAVEN_HOME"
  }
  stages{
    stage("Maven Build"){
        steps {
			container('maven-without-settings') {
				sh 'mvn -B verify --file lemminx-maven/pom.xml -Dmaven.test.error.ignore=true -Dmaven.test.failure.ignore=true -Dmaven.repo.local=$WORKSPACE/.m2/repository'
			}
        }
        post {
			always {
				junit 'lemminx-maven/target/surefire-reports/TEST-*.xml'
			}
		}
    }
    stage ('Deploy Maven artifacts') {
      when {
          branch 'master'
      }
      steps {
        container('maven-with-settings') {
          sh 'mvn -B deploy  --file lemminx-maven/pom.xml -DskipTests -Dcbi.jarsigner.skip=false -Dmaven.repo.local=$WORKSPACE/.m2/repository'
        }
      }
    }
  }
}
