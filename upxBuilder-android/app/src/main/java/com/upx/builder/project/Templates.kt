package com.upx.builder.project

import com.upx.builder.editor.Language
import java.io.File

/** A starter project scaffold for one of the supported languages. */
data class ProjectTemplate(
    val id: String,
    val displayName: String,
    val language: Language,
    val description: String,
    /** Short, comprehensive "how to write code in this language" guide shown in-app. */
    val guide: String,
    /** Files to write, keyed by path relative to the project root. */
    val files: (projectName: String) -> Map<String, String>,
) {
    /** Materialise the template into [parent]/[projectName] and return the created root. */
    fun create(parent: File, projectName: String): File {
        val root = File(parent, projectName)
        root.mkdirs()
        files(projectName).forEach { (relPath, content) ->
            val target = File(root, relPath)
            target.parentFile?.mkdirs()
            target.writeText(content)
        }
        return root
    }
}

object Templates {

    val flutter = ProjectTemplate(
        id = "flutter",
        displayName = "Flutter App",
        language = Language.DART,
        description = "A cross-platform Flutter application skeleton (Dart).",
        guide = """
            # Writing Flutter (Dart) code

            1. Everything is a Widget. Compose your UI by nesting widgets.
            2. Use `StatelessWidget` for static UI and `StatefulWidget` when the
               screen changes over time; call `setState(() { … })` to redraw.
            3. `main()` calls `runApp(MyApp())` — that is your entry point.
            4. Run on a device with `flutter run`; hot-reload with `r` in the terminal.
            5. Add packages in `pubspec.yaml`, then run `flutter pub get`.

            Tip: keep `build()` methods small — extract sub-widgets for readability.
        """.trimIndent(),
        files = { name ->
            mapOf(
                "pubspec.yaml" to """
                    name: ${name.lowercase().replace('-', '_')}
                    description: A new Flutter project created with upxBuilder.
                    publish_to: 'none'
                    version: 1.0.0+1

                    environment:
                      sdk: '>=3.0.0 <4.0.0'

                    dependencies:
                      flutter:
                        sdk: flutter
                      cupertino_icons: ^1.0.6

                    dev_dependencies:
                      flutter_test:
                        sdk: flutter

                    flutter:
                      uses-material-design: true
                """.trimIndent(),
                "lib/main.dart" to """
                    import 'package:flutter/material.dart';

                    void main() => runApp(const MyApp());

                    class MyApp extends StatelessWidget {
                      const MyApp({super.key});

                      @override
                      Widget build(BuildContext context) {
                        return MaterialApp(
                          title: '$name',
                          theme: ThemeData(colorSchemeSeed: Colors.indigo, useMaterial3: true),
                          home: const HomePage(),
                        );
                      }
                    }

                    class HomePage extends StatefulWidget {
                      const HomePage({super.key});
                      @override
                      State<HomePage> createState() => _HomePageState();
                    }

                    class _HomePageState extends State<HomePage> {
                      int _count = 0;

                      @override
                      Widget build(BuildContext context) {
                        return Scaffold(
                          appBar: AppBar(title: const Text('$name')),
                          body: Center(child: Text('Tapped ${'$'}_count times')),
                          floatingActionButton: FloatingActionButton(
                            onPressed: () => setState(() => _count++),
                            child: const Icon(Icons.add),
                          ),
                        );
                      }
                    }
                """.trimIndent(),
                "README.md" to "# $name\n\nA Flutter app created with upxBuilder. Run with `flutter run`.\n",
            )
        },
    )

    val cpp = ProjectTemplate(
        id = "cpp",
        displayName = "C++ Project",
        language = Language.CPP,
        description = "A C++ project built with CMake.",
        guide = """
            # Writing C++ code

            1. Execution starts in `int main()`. Return 0 for success.
            2. `#include` headers for the features you use (`<iostream>` for I/O).
            3. Declare types before use; prefer `std::` containers over raw arrays.
            4. Build with CMake: `cmake -B build && cmake --build build`.
            5. Manage memory with smart pointers (`std::unique_ptr`) rather than raw `new`.

            Tip: compile with warnings on (`-Wall -Wextra`) to catch mistakes early.
        """.trimIndent(),
        files = { name ->
            mapOf(
                "CMakeLists.txt" to """
                    cmake_minimum_required(VERSION 3.16)
                    project($name CXX)

                    set(CMAKE_CXX_STANDARD 17)
                    set(CMAKE_CXX_STANDARD_REQUIRED ON)

                    add_executable($name src/main.cpp)
                """.trimIndent(),
                "src/main.cpp" to """
                    #include <iostream>
                    #include <string>

                    int main() {
                        std::string name = "$name";
                        std::cout << "Hello from " << name << "!" << std::endl;
                        return 0;
                    }
                """.trimIndent(),
                "README.md" to "# $name\n\nA C++ project created with upxBuilder.\n\n```\ncmake -B build\ncmake --build build\n./build/$name\n```\n",
            )
        },
    )

    val java = ProjectTemplate(
        id = "java",
        displayName = "Java Project",
        language = Language.JAVA,
        description = "A plain Java project.",
        guide = """
            # Writing Java code

            1. Code lives in classes; the entry point is `public static void main(String[] args)`.
            2. One public class per file, and the file name must match the class name.
            3. Compile with `javac Main.java`, run with `java Main`.
            4. Use packages (`package com.example;`) to organise larger projects.
            5. Prefer `List`, `Map` and the Streams API over manual loops where it reads clearly.

            Tip: keep methods short and give variables descriptive names.
        """.trimIndent(),
        files = { name ->
            mapOf(
                "src/Main.java" to """
                    public class Main {
                        public static void main(String[] args) {
                            System.out.println("Hello from $name!");
                        }
                    }
                """.trimIndent(),
                "README.md" to "# $name\n\nA Java project created with upxBuilder.\n\n```\njavac src/Main.java -d out\njava -cp out Main\n```\n",
            )
        },
    )

    val kotlin = ProjectTemplate(
        id = "kotlin",
        displayName = "Kotlin Project",
        language = Language.KOTLIN,
        description = "A Kotlin project built with Gradle.",
        guide = """
            # Writing Kotlin code

            1. The entry point is the top-level `fun main()` — no class required.
            2. Use `val` for read-only values and `var` only when you must reassign.
            3. Types are inferred; add them explicitly on public APIs for clarity.
            4. Build/run with Gradle: `gradle run` (or `./gradlew run`).
            5. Embrace null-safety: `?`, `?.` and `?:` keep null errors out of runtime.

            Tip: data classes (`data class`) give you equals/hashCode/toString for free.
        """.trimIndent(),
        files = { name ->
            mapOf(
                "build.gradle.kts" to """
                    plugins {
                        kotlin("jvm") version "2.1.0"
                        application
                    }

                    repositories { mavenCentral() }

                    application { mainClass.set("MainKt") }
                """.trimIndent(),
                "settings.gradle.kts" to "rootProject.name = \"$name\"\n",
                "src/main/kotlin/Main.kt" to """
                    fun main() {
                        println("Hello from $name!")
                    }
                """.trimIndent(),
                "README.md" to "# $name\n\nA Kotlin project created with upxBuilder. Run with `gradle run`.\n",
            )
        },
    )

    val python = ProjectTemplate(
        id = "python",
        displayName = "Python Project",
        language = Language.PYTHON,
        description = "A Python script project.",
        guide = """
            # Writing Python code

            1. Execution starts at the top of the file; guard entry with
               `if __name__ == "__main__":`.
            2. Indentation IS the syntax — use 4 spaces per level, consistently.
            3. Run with `python3 main.py`; no compilation step needed.
            4. Install packages with `pip install <name>` (use a virtualenv).
            5. Functions are defined with `def`, classes with `class`.

            Tip: f-strings (`f"x = {x}"`) are the cleanest way to format text.
        """.trimIndent(),
        files = { name ->
            mapOf(
                "main.py" to """
                    def greet(name: str) -> str:
                        return f"Hello from {name}!"


                    if __name__ == "__main__":
                        print(greet("$name"))
                """.trimIndent(),
                "README.md" to "# $name\n\nA Python project created with upxBuilder. Run with `python3 main.py`.\n",
            )
        },
    )

    val javascript = ProjectTemplate(
        id = "javascript",
        displayName = "JavaScript Project",
        language = Language.JAVASCRIPT,
        description = "A Node.js JavaScript project.",
        guide = """
            # Writing JavaScript code

            1. Run with Node.js: `node index.js`.
            2. Use `const` by default, `let` when you must reassign — avoid `var`.
            3. Functions: `function f() {}` or arrows `const f = () => {}`.
            4. Add packages with `npm install <name>`; they land in node_modules.
            5. Use `async`/`await` for asynchronous work instead of raw callbacks.

            Tip: template literals (`` `x = ${'$'}{x}` ``) make string building easy.
        """.trimIndent(),
        files = { name ->
            mapOf(
                "index.js" to """
                    function greet(name) {
                      return `Hello from ${'$'}{name}!`;
                    }

                    console.log(greet("$name"));
                """.trimIndent(),
                "package.json" to """
                    {
                      "name": "${name.lowercase()}",
                      "version": "1.0.0",
                      "main": "index.js",
                      "scripts": { "start": "node index.js" }
                    }
                """.trimIndent(),
                "README.md" to "# $name\n\nA JavaScript project created with upxBuilder. Run with `node index.js`.\n",
            )
        },
    )

    val go = ProjectTemplate(
        id = "go",
        displayName = "Go Project",
        language = Language.GO,
        description = "A Go module project.",
        guide = """
            # Writing Go code

            1. Every file starts with `package <name>`; the entry point is
               `func main()` in package main.
            2. Run with `go run .`, build a binary with `go build`.
            3. Unused imports and variables are compile errors — keep code tidy.
            4. Errors are values: check `if err != nil { ... }` after calls.
            5. Format automatically with `gofmt -w .` — Go has one true style.

            Tip: goroutines (`go f()`) and channels make concurrency simple.
        """.trimIndent(),
        files = { name ->
            mapOf(
                "go.mod" to "module ${name.lowercase()}\n\ngo 1.21\n",
                "main.go" to """
                    package main

                    import "fmt"

                    func main() {
                        fmt.Println("Hello from $name!")
                    }
                """.trimIndent(),
                "README.md" to "# $name\n\nA Go project created with upxBuilder. Run with `go run .`.\n",
            )
        },
    )

    val all: List<ProjectTemplate> = listOf(flutter, cpp, java, kotlin, python, javascript, go)

    fun byId(id: String): ProjectTemplate? = all.firstOrNull { it.id == id }
}
