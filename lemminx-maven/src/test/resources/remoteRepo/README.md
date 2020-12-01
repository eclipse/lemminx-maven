This is a dummy Maven repo to be used as remote one for tests. It include a few unique artifacts and an index.

To generate it:

```sh
touch dummy.jar
for pom in $(ls -1 *.pom.xml); do
	mvn install:install-file -Dfile=dummy.jar -DpomFile=$pom -Dmaven.repo.local=.
done
rm dummy.jar
mkdir .index && pushd .index
java -jar ~/.m2/repository/org/apache/maven/indexer/indexer-cli/6.0.0/indexer-cli-6.0.0.jar index -i .index/ -r ..
popd
```