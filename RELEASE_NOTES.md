# LemMinx-Maven Releases

## Upcoming Release



## Latest Release

### 0.2.0

* 📅 Release Date (tentative): December 1st, 2020
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

