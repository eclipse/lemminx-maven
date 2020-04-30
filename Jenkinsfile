pipeline{
	agent {
		kubernetes {
			label 'lemminx-maven-pod4'
			defaultContainer 'jnlp'
			// We use a pod with alpine Maven because we don't want the local
			// filesystem cache for maven deps, which causes issues later in
			// tests.
			// This is actually a limitation of Lemminx Maven not propertly 
			// supporting local filesystem repos of whatever configuration used
			// by default jnlp. When Lemmin Maven supports that, let's get back
			// to "agent any" which saves a lot of downloads.
			yaml """
apiVersion: 'v1'
kind: 'Pod'
metadata:
  annotations: {}
  labels:
    jenkins: 'slave'
    jenkins/label: ''
  name: 'basic-agent-zw41r'
spec:
  containers:
  - env:
    - name: 'MAVEN_OPTS'
      value: '-Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn'
    - name: 'JENKINS_SECRET'
      value: '********'
    - name: 'JENKINS_TUNNEL'
      value: 'jenkins-discovery.lemminx.svc.cluster.local:50000'
    - name: 'JENKINS_AGENT_NAME'
      value: 'basic-agent-zw41r'
    - name: 'MAVEN_CONFIG'
      value: '-B -e'
    - name: 'JENKINS_NAME'
      value: 'basic-agent-zw41r'
    - name: 'JENKINS_AGENT_WORKDIR'
      value: '/home/jenkins/agent'
    - name: 'JENKINS_URL'
      value: 'http://jenkins-ui.lemminx.svc.cluster.local/lemminx/'
    - name: 'HOME'
      value: '/home/jenkins'
    image: 'eclipsecbijenkins/basic-agent:3.35'
    imagePullPolicy: 'Always'
    name: 'jnlp'
    resources:
      limits:
        memory: '4096Mi'
        cpu: '2000m'
      requests:
        memory: '4096Mi'
        cpu: '1000m'
    securityContext:
      privileged: false
    tty: true
    volumeMounts:
    - mountPath: '/home/jenkins/.m2/toolchains.xml'
      name: 'toolchains-xml'
      readOnly: true
      subPath: 'toolchains.xml'
    - mountPath: '/opt/tools'
      name: 'volume-0'
      readOnly: false
    - mountPath: '/home/jenkins'
      name: 'volume-2'
      readOnly: false
    - mountPath: '/home/jenkins/.m2/repository'
      name: 'volume-3'
      readOnly: false
    - mountPath: '/home/jenkins/.m2/settings-security.xml'
      name: 'settings-security-xml'
      readOnly: true
      subPath: 'settings-security.xml'
    - mountPath: '/home/jenkins/.m2/wrapper'
      name: 'volume-4'
      readOnly: false
    - mountPath: '/home/jenkins/.m2/settings.xml'
      name: 'settings-xml'
      readOnly: true
      subPath: 'settings.xml'
    - mountPath: '/home/jenkins/.ssh'
      name: 'volume-1'
      readOnly: false
    - mountPath: '/home/jenkins/agent'
      name: 'workspace-volume'
      readOnly: false
    workingDir: '/home/jenkins/agent'
  nodeSelector:
    beta.kubernetes.io/os: 'linux'
  restartPolicy: 'Never'
  securityContext: {}
  volumes:
  - name: 'settings-security-xml'
    secret:
      items:
      - key: 'settings-security.xml'
        path: 'settings-security.xml'
      secretName: 'm2-secret-dir'
  - name: 'volume-0'
    persistentVolumeClaim:
      claimName: 'tools-claim-jiro-lemminx'
      readOnly: true
  - configMap:
      items:
      - key: 'toolchains.xml'
        path: 'toolchains.xml'
      name: 'm2-dir'
    name: 'toolchains-xml'
  - emptyDir:
      medium: ''
    name: 'volume-2'
  - configMap:
      name: 'known-hosts'
    name: 'volume-1'
  - name: 'settings-xml'
    secret:
      items:
      - key: 'settings.xml'
        path: 'settings.xml'
      secretName: 'm2-secret-dir'
  - emptyDir:
      medium: ''
    name: 'workspace-volume'
  - emptyDir:
      medium: ''
    name: 'volume-4'
  - emptyDir:
      medium: ''
    name: 'volume-3'
"""
		}
	}
  environment {
    MAVEN_HOME = "$WORKSPACE/.m2/"
    MAVEN_USER_HOME = "$MAVEN_HOME"
  }
  tools {
    jdk 'adoptopenjdk-hotspot-jdk8-latest'
    maven 'apache-maven-latest'
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
