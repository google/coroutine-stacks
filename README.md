# Coroutine Stacks
![example workflow](https://github.com/nikita-nazarov/coroutine-stacks/actions/workflows/gradle.yml/badge.svg)

This project was developed during Google Summer of Code 2023 and is dedicated to creating an Intellij Plugin that will enhance the coroutine debugging experience by creating a view with the graph representation of coroutines and their stack traces, similar to how it is done in the  [Parallel Stacks](https://www.jetbrains.com/help/rider/Debugging_Multithreaded_Applications.html#parallel-stacks) feature of the JetBrains Rider IDE.

![coroutine-stacks-demo](https://github.com/google/coroutine-stacks/assets/25721619/7b8caf0c-ad82-476c-91b8-3cac105155cf)


## How to install the plugin
The plugin is published in [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/23117-coroutine-stacks/), so you can install it from the `Plugins` menu in the IDE.

## How to use the plugin
Once you start the debugger click on the `Coroutine Stacks` label in the bottom right corner of the IDE. If you use the new UI you should click on the icon with four circles froming a square. After that you will see a panel with coroutine stack traces. On the top of it you will find a couple of useful buttons:
1. Add library frames filter
2. Capture a coroutine dump
3. Add coroutine creation stack traces to the panel
4. Select the dispatcher
5. Zoom the panel in and out

Check out the [Quick Start Guide](https://plugins.jetbrains.com/plugin/23117-coroutine-stacks/documentation/quick-start-guide) for the detailed description of the plugin features.
