# How to Contribute

Contributions are essential for keeping this language server extension great. We try to keep it as easy as possible to contribute changes and we are open to suggestions for making it even easier. There are only a few guidelines that we need contributors to follow.

## Development

### Installation Prerequisites:

  * [JDK 17+](https://adoptopenjdk.net/)
  * [Maven 3.9.3+](https://maven.apache.org/)

### Lemmix Language Server

LemMinX-Maven is an extension to Lemminx Language Server, so you may need to clone and build Lemminx before start contributing to LemMinX-Maven. See [Lemminx Contribution](https://raw.githubusercontent.com/eclipse/lemminx/main/CONTRIBUTING.md) Guide on how to get and build Lemminx Language Server 

### Steps

1. Fork and clone the LemMinX-Maven repository:

```
git clone https://github.com/eclipse/lemminx-maven.git
```

2. Build/Test LemMinX-Maven on Mac/Linux:
	```bash
	$ ./mvnw verify
	```
	or for Windows:
	```bash
	$ mvnw.cmd verify
	```

### Debug

The LemMinX-Maven extension must be debugged remotely as it's most useful when connected to a client. In order to debug, one needs to look at whether the specific language client provides such a capability. For example :

* [M2E Maven POM File Editor (Wild Web Developer, LemMinX, LS)](https://github.com/eclipse-m2e/m2e-core/tree/master) - See : [How to... develop and debug m2e and lemminx-maven integration](https://github.com/eclipse-m2e/m2e-core/blob/master/org.eclipse.m2e.editor.lemminx/HOWTO-DEV.md)

#### Building and running LemMinX-Maven in [VSCode-XML](https://github.com/redhat-developer/vscode-xml/blob/main/README.md#contributing) extension.

1. Build LemMinX-Maven set of dependency and extension Jars for VSCode-XML extension:

	```bash
	$ ./mvnw verify -DskipTests -Pgenerate-vscode-jars
	```
	or for Windows:
	```bash
	$ mvnw.cmd verify -DskipTests -Pgenerate-vscode-jars
	```

This produces the `<LemMinX-Maven>/lemminx-maven/target/vscode-lemminx-maven-jars` directory containing all the Jars required to run LemMinX-Maven extension in VSCode XML as well as the `lemminx-maven-<version>-vscode-uber-jars.zip` Zip-archive with the contents of this directory.

2. Clone and build VSCode-XML extension, See: [VSCode-XML Extension Contribution Guide](https://github.com/redhat-developer/vscode-xml/blob/main/CONTRIBUTING.md#steps).

3. Try running the VSCode-XML extension to make sure everything is correctly installed and XML editor works (validation, content assist, hovers, etc. for XML tags and attributes).

4. Copy `<LemMinX-Maven>/lemminx-maven/target/vscode-lemminx-maven-jars` directory to VSCode-XML extension project directory and make sure it's visible in `vscode-xml`project in VSCode .

5. In VSCode modify `vscode-xml/package.json` adding the following configuration to the `contributes` section:

```json
  "xml.javaExtensions": [
     "./vscode-lemminx-maven-jars/*.jar"
   ],
```

6. Restart VSCode-XML extension and try editing a Maven project file (Maven project validation, content assist for group ID, artifact ID and versions, hovers for artifacts and Maven properties, Maven properties refactoring etc.) 

### Pull Requests

In order to submit contributions for review, please make sure you have signed the [Eclipse Contributor Agreement](https://www.eclipse.org/legal/ecafaq.php) (ECA) with your account.
