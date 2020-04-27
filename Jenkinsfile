pipeline{
	agent {
		kubernetes {
			label 'lemminx-maven-pod2'
			defaultContainer 'jnlp'
			yaml """
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: maven
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
  - name: jnlp
    image: 'eclipsecbi/jenkins-jnlp-agent'
    volumeMounts:
    - mountPath: "/home/jenkins/.m2/settings-security.xml"
      name: "settings-security-xml"
      readOnly: true
      subPath: "settings-security.xml"
    - mountPath: "/home/jenkins/.m2/settings.xml"
      name: "settings-xml"
      readOnly: true
  volumes:
  - name: "settings-security-xml"
    secret:
      items:
      - key: "settings-security.xml"
        path: "settings-security.xml"
      secretName: "m2-secret-dir"
  - name: "settings-xml"
    secret:
      items:
      - key: "settings.xml"
        path: "settings.xml"
      secretName: "m2-secret-dir"
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
			container('maven') {
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
        withMaven {
          sh 'mvn -B deploy  --file lemminx-maven/pom.xml -DskipTests -Dcbi.jarsigner.skip=false -Dmaven.repo.local=$WORKSPACE/.m2/repository'
        }
      }
    }
  }
}
