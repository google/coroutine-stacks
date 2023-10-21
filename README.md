# Coroutine Stacks
![example workflow](https://github.com/nikita-nazarov/coroutine-stacks/actions/workflows/gradle.yml/badge.svg)

This project was developed during Google Summer of Code 2023 and is dedicated to creating an Intellij Plugin that will enhance the coroutine debugging experience by creating a view with the graph representation of coroutines and their stack traces, similar to how it is done in the  [Parallel Stacks](https://www.jetbrains.com/help/rider/Debugging_Multithreaded_Applications.html#parallel-stacks) feature of the JetBrains Rider IDE.

![coroutine-stacks-demo](https://github.com/google/coroutine-stacks/assets/25721619/7b8caf0c-ad82-476c-91b8-3cac105155cf)


## How to install the plugin
The plugin is still going through the approval process before the release and will be released later. But you can still build and install it manually.

## How to use the plugin
Once you start the debugger click on the `Coroutine Stacks` label in the bottom right corner of the IDE. After that you will see a panel with coroutine stack traces. On the top of it you will find a couple of useful buttons:
1. Add library frames filter
2. Capture a coroutine dump
3. Add coroutine creation stack traces to the panel
4. Select the dispatcher
5. Zoom the panel in and out

Check out this [blog post](https://medium.com/@raehatsinghnanda/parallel-stacks-for-kotlin-coroutines-in-the-debugger-d3099eb3a9c2) for the full feature breakdown.
