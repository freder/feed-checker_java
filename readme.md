Checks a list of RSS / Atom feeds for new posts.


## build

```shell
mvn clean package
```


## run

```shell
mvn exec:java -Dexec.mainClass="freder.feedchecker.App" -Dexec.args="<command>"
# or
java -jar target/feed-checker-1.0-SNAPSHOT-jar-with-dependencies.jar <command>
```


### Commands

```shell
add    <feed-url>  # add a feed
remove <feed-url>  # remove a feed
list               # list all feeds
check              # check all feeds for new items
```
