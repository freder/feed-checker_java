## build

```shell
mvn clean package
```


## run

```shell
mvn exec:java -Dexec.mainClass="freder.App" -Dexec.args="<args>"
# or
java -jar target/feed-checker-1.0-SNAPSHOT-jar-with-dependencies.jar <args>
```
