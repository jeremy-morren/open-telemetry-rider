# Third party notices

The Open Telemetry Debug Viewer plugin is distributed with the third party components listed below.
Their exact versions are the file names in the plugin's `lib` directory.

Full license texts are distributed alongside this file:

- [Apache License 2.0](licenses/Apache-2.0.txt)
- [BSD 3-Clause License (Protocol Buffers)](licenses/BSD-3-Clause-protobuf.txt)

## Bundled libraries

| Component                                                                            | Project                                                                     | License      |
| ------------------------------------------------------------------------------------ | --------------------------------------------------------------------------- | ------------ |
| `jackson-core`, `jackson-databind`, `jackson-annotations`, `jackson-datatype-jsr310` | [FasterXML Jackson](https://github.com/FasterXML/jackson)                   | Apache-2.0   |
| `protobuf-java`, `protobuf-java-util`                                                | [Protocol Buffers](https://github.com/protocolbuffers/protobuf)             | BSD-3-Clause |
| `gson`                                                                               | [Google Gson](https://github.com/google/gson)                               | Apache-2.0   |
| `error_prone_annotations`                                                            | [Error Prone](https://github.com/google/error-prone)                        | Apache-2.0   |
| `jsr305`                                                                             | [FindBugs JSR-305 annotations](https://github.com/findbugsproject/findbugs) | Apache-2.0   |

## Generated code

The OTLP message classes in `io.opentelemetry.proto.*` are compiled during the build from the
`.proto` schemas of [opentelemetry-proto](https://github.com/open-telemetry/opentelemetry-proto),
Copyright The OpenTelemetry Authors, licensed under Apache-2.0.

## Notices

Jackson: this product includes software developed by FasterXML.net (https://fasterxml.com).

Gson, Error Prone: Copyright Google Inc./Google LLC.

Protocol Buffers: Copyright 2008 Google Inc. All rights reserved.
