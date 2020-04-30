pipeline{
	agent {
		kubernetes {
			label 'lemminx-maven-pod8'
			defaultContainer 'jnlp'
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
    image: 'eclipsecbijenkins/basic-agent:3.35'
    volumeMounts:
    - mountPath: "/home/jenkins/.m2/settings-security.xml"
      name: "settings-security-xml"
      readOnly: true
      subPath: "settings-security.xml"
    - mountPath: "/home/jenkins/.m2/settings.xml"
      name: "settings-xml"
      readOnly: true
      subPath: "settings.xml"
    - mountPath: "/opt/tools"
      name: "volume-0"
      readOnly: false
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
  - name: "volume-0"
    persistentVolumeClaim:
      claimName: "tools-claim-jiro-lemminx"
      readOnly: true
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
        container('jnlp') {
          sh '/opt/tools/apache-maven/3.6.3/bin/mvn -B deploy  --file lemminx-maven/pom.xml -DskipTests -Dcbi.jarsigner.skip=false -Dmaven.repo.local=$WORKSPACE/.m2/repository'
        }
      }
    }
  }
}
