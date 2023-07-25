# How to add a test case
1. Capture a coroutine dump of an application of interest and paste it in `dumps/`
2. Run a script to build a coroutine forest: `python parse_dumps.py`. Don't forget to install requirements with `pip install requirements.txt`
3. Add a corresponding test case to `CoroutineStacksFromDumpTest`
