This repo is an Android app repo using Jetpack Compose and Kotlin. It is meant
to be a replacement for the official Sony PlayStation App that allows users
to track trophies earned etc. for their account.

We have a Rust core library dependency that we FFI into from Kotlin (using
uniffi). This Rust core library uses tokio under the hood and makes async network
requests to Sony servers to get the right info, provided it is given the right
auth data.

## Workflows

The repo has quite a lot of tooling to make LLM agents' lives easier. Chief
among these is 'andy', a tool that can help drive the app on a real device
in an LLM-friendly fashion. See [the skill file](.agents/skills/android-emulator)
for more info.

### Base flow

At the end of every significant change (this can include doing work that should
solve a given issue), you need to always follow these steps:

1. Build the debug APK

2. Use "adb install -r <path-to-newly-built-apk>" to install it to the Android device

3. Launch a subagent that will use the android-emulator skill to launch the APK and drive it.

This way, you can evaluate the tool and figure out if the bug/issue is still present.

If it is, subagent can relay this info (basically what the issue is and where it got the issue)
back to the main thread, which can take further action.

### Dogfooding

Sometimes user may ask to dogfood the app. This means they want you to do this
three step process with the intention of finding bugs and issues in the app.

You have to be thorough when you are driving the phone app. Do not leave any stone unturned.

Once this is done, you are to file these bug reports into the "bettertrophies"
sourcehut bug tracker (see hut-cli skill for more info)

### "Solve ticket X"

If user asks to "solve ticket X", it means:

1. Get the bug report from the sourcehut bug tracker (see hut-cli skill)

2. Understand it thoroughly

3. Try to fix the issue

4. Test your fix by running the app as described above in basic flow

5. Verify the fix works.

6. Reply to the original ticket with summary of your changes.
