# LemMinx-Maven Releases

## Upcoming Release

### 0.3.2

* 📅 Release Date: ?
* All changes: https://github.com/eclipse/lemminx-maven/compare/0.3.1...0.3.2

## Latest Release

### 0.3.1

* 📅 Release Date: June 15th, 2021
* All changes: https://github.com/eclipse/lemminx-maven/compare/0.3.0...0.3.1

#### Resolve dependencies against workspace projects

Maven projects that are already visible to the Language Server (their root folder is set as a `workspaceFolder` or a `pom.xml` file was already visible) are now used with high priority when resolving dependencies. As a result, the change that happen in the workspace now affect resolution of other projects in this same workspace.

This allows to get faster and more reactive feedback when working across multiple projects.

#### Fix resolution of ${basedir}

`${basedir}` wasn't properly resolved in some cases, leading to suboptimal assistance or error detection. This is now fixed, so a bunch of features should now work slightly better.

### 0.3.0

* 📅 Release Date: February 19th, 2021
* All changes: https://github.com/eclipse/lemminx-maven/compare/0.2.0...0.3.0

#### Use LemMinX 0.15

This allows to consume newer APIs. As a result, LemMinX-Maven 0.3.0 is __not__ compatible with LemMinX 0.14.x and older.

#### Fixed consumption of Maven settings

Maven settings were not properly honored in previous release. Some bug was fixed and Maven settings (eg proxy, mirrors and so on) are now supposed to be used.

#### Resolve dependencies and parent from workspace

Using LemMinX 0.15 allows LemMinX-Maven to leverage with `workspaceFolders` LSP operation. LemMinX-Maven is now able to resolve dependencies and parent references that were declared as workspaceFolders, additionally to the various local and remote repositories.

#### New format for settings/configuration

To better align with VSCode requirements, the configuration/initializationOptions are now expected to be passed as a hierarchy of JSON objects, not as flat `.`-separated properties.

#### Completion of properties without ${ prefix

Properties are now suggested for completion without having to prefix with `${`.

#### Bugfixes & Silence some exceptions

Some exceptions are now avoided, and some remaining not-so-excetional exceptions are now silenced.

### 0.2.0

* 📅 Release Date: December 3rd, 2020
* All changes: https://github.com/eclipse/lemminx-maven/compare/0.1.1...0.2.0

#### Important fixes

Some important fixes took place, mainly in project initialization and resolution. It should notably almost annihilate the occurrences of "false-positive" diagnostics about unresolved Maven plugins.

#### Better index management

Index of Maven repositories are now only downloaded on request, not on initialization. This allows to keep good performance of LemMinX in general, without extra CPU cycles when editing unrelated XML files, or unrelated pom.xml parts.

A new initialization/workspace setting option is available to skip download of index files. It's `maven.index.skip`.

#### Support LemMinX 0.14.x

LemMinX-Maven is an extension for LemMinX 0.14.x; it's currently built and tested against LemMinX 0.14.1.

#### Available as "zip-with-dependencies"

This extension is published via Maven and now includes a `-zip-with-dependencies.zip` artifact, which is convenient to use for some integrations.

### 0.1.1

* 📅 Release Date: 2nd September 2020
* All changes: https://github.com/eclipse/lemminx-maven/compare/0.1.0...0.1.1

#### Completion for nested elements in plugin configuration

LemMinX-Maven now introspects plugin definition and can provide completion for nested elements in plugin configuration.

#### Changed index location

Default index location is now `${maven.local.repository}/index` (instead of previous `${user.home}/.lemminx/maven`.

The index location can now be configured by setting its path as value of the `maven.indexLocation` element in initializationOptions, as a child of the `xml` element.
```xml
{
	"xml": {
		"maven.indexLocation": "/home/me/myIndexLocation"
	}
}
```

#### Default remote repositories now better considered

Some major bug was preventing from downloading remote artifacts from Central in some cases. This bug should now be fixed.

## Previous releases

(previous releases are not documented)

