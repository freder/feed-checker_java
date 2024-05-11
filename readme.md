Checks a list of RSS / Atom feeds for new posts.


## setup

create a `feeds.json` file à la:

```json
{
	"FreshRSS": "https://github.com/FreshRSS/FreshRSS/releases.atom"
}
```


## build

```shell
mvn clean package
```


## run

```shell
mvn exec:java -Dexec.mainClass="freder.App" -Dexec.args="<command>"
# or
java -jar target/feed-checker-1.0-SNAPSHOT-jar-with-dependencies.jar <command>
```


### Commands

```shell
list   # list all feeds
check  # check all feeds for new items
```
