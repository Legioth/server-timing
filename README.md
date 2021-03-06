# Server Timing

Vaadin add-on that helps report server-side processing times to the developer tooling in the browser.

For usage examples, see [the demo view](https://github.com/Legioth/server-timing/blob/master/src/test/java/org/github/legioth/servertiming/DemoView.java).

## Development instructions

Starting the test/demo server:
1. Run `mvn jetty:run`.
2. Open http://localhost:8080 in the browser.

## Publishing to Vaadin Directory

You can create the zip package needed for [Vaadin Directory](https://vaadin.com/directory/) using
```
mvn versions:set -DnewVersion=1.0.0 # You cannot publish snapshot versions 
mvn install -Pdirectory
```

The package is created as `target/server-timing-1.0.0.zip`

For more information or to upload the package, visit https://vaadin.com/directory/my-components?uploadNewComponent
