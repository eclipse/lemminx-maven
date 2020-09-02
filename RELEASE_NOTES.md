# LemMinx-Maven Releases

## Upcoming Release

### 0.1.2

* 📅 Release Date (tentative): Early December 2020
* All changes: https://github.com/eclipse/lemminx-maven/compare/0.1.1...0.1.2

## Latest Release

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

