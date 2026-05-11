# Alexa Google TV Bridge using Firebase

Control your Google TV / Android TV using Alexa voice commands.

This project uses:
- Custom Alexa Skill
- AWS Lambda
- Firebase Realtime Database
- Android TV Companion App

The Alexa command is sent to Firebase, and the TV app reads the command and performs the action.

---

# Features

- Open apps on Google TV using any Alexa-enabled device
- Works with Google TV and Android TV
- Search inside apps using voice
- Can even work with soundbars that have built-in Alexa

---

# Architecture

```text
Alexa Speaker / Alexa App
        ↓
Custom Alexa Skill
        ↓
AWS Lambda
        ↓
Firebase Realtime Database
        ↓
Android TV Companion App
        ↓
Launch Apps on TV
```

---

# Demo Commands

```text
Alexa, ask {invocation name} to open YouTube
Alexa, ask {invocation name} to open Netflix
Alexa, ask {invocation name} to go home
Alexa, ask {invocation name} to search YouTube
```

For search commands, Alexa will ask what you want to search.

---

# Requirements

## Accounts

These services are free (with monthly limits):

- AWS Account
- Firebase Account

## Software

- Android Studio
- ADB (Android Debug Bridge)

---

# Part 1 — Firebase Setup

## 1. Create Firebase Project

Open Firebase Console:

https://console.firebase.google.com/

Create a new project.

---

## 2. Enable Realtime Database

Go to:

```text
Build → Realtime Database
```

Create a database.

For testing, use these rules:

```json
{
  "rules": {
    ".read": true,
    ".write": true
  }
}
```

---

## 3. Generate Firebase Service Account Key

Go to:

```text
Project Settings → Service Accounts
```

Generate a private key and download it.

Rename the downloaded file to:

```text
firebase-key.json
```

---

# Part 2 — Alexa Skill Setup

## 1. Create Alexa Skill

Open:

https://developer.amazon.com/alexa/console/ask

Create:
- Custom Skill
- Alexa-hosted (Node.js)

---

## 2. Set Invocation Name

Example:

```text
media bridge
```

---

## 3. Create Intent

Intent name:

```text
OpenAppIntent
```

---

## 4. Add Slot

| Slot Name | Type |
|---|---|
| app | AMAZON.SearchQuery |

---

## 5. Add Utterances

```text
run {app}
launch {app}
start {app}
open {app}
```

---

## 6. Install Firebase Admin SDK

Inside `package.json`:

```json
"firebase-admin": "^11.10.1"
```

---

## 7. Upload Firebase Key

Upload:

```text
firebase-key.json
```

to the Alexa skill code editor.

### Steps

1. Create a folder named:

```text
lambda
```

2. Put `firebase-key.json` inside it

3. Zip the folder

4. Upload it using the import code option

---

# Alexa Lambda Code

This code:
- Gets the voice command from Alexa
- Sends the command to Firebase
- The Android TV app reads the command from Firebase and performs the action

```javascript
/* *
 * This sample demonstrates handling intents from an Alexa skill using the Alexa Skills Kit SDK (v2).
 * Please visit https://alexa.design/cookbook for additional examples on implementing slots, dialog management,
 * session persistence, api calls, and more.
 * */
const Alexa = require('ask-sdk-core');
const admin = require('firebase-admin');

const serviceAccount =
    require('./firebase-key.json');

if (!admin.apps.length) {
    admin.initializeApp({
        credential: admin.credential.cert(serviceAccount),
        databaseURL: "your firebase database url"
    });
}

const db = admin.database();
const LaunchRequestHandler = {
    canHandle(handlerInput) {
        return Alexa.getRequestType(handlerInput.requestEnvelope) === 'LaunchRequest';
    },
    handle(handlerInput) {
        const speakOutput = 'Welcome, you can say Hello or Help. Which would you like to try?';

        return handlerInput.responseBuilder
            .speak(speakOutput)
            .reprompt(speakOutput)
            .getResponse();
    }
};

const HelloWorldIntentHandler = {
    canHandle(handlerInput) {
        return Alexa.getRequestType(handlerInput.requestEnvelope) === 'IntentRequest'
            && Alexa.getIntentName(handlerInput.requestEnvelope) === 'HelloWorldIntent';
    },
    handle(handlerInput) {
        const speakOutput = 'Hello World!';

        return handlerInput.responseBuilder
            .speak(speakOutput)
            //.reprompt('add a reprompt if you want to keep the session open for the user to respond')
            .getResponse();
    }
};
const GoHomeIntentHandler = {
    canHandle(handlerInput) {
        return Alexa.getRequestType(handlerInput.requestEnvelope) === 'IntentRequest'
            && Alexa.getIntentName(handlerInput.requestEnvelope) === 'GoHomeIntent';
    },
    async handle(handlerInput) {
        try {
            if (!admin.apps.length) {
                admin.initializeApp({
                    credential: admin.credential.cert(serviceAccount),
                    databaseURL: "your firebase database url/"
                });
            }
            const db = admin.database();

            await db.ref("tv_commands/current").set({
                action: "home",
                timestamp: Date.now()
            });

            await admin.app().delete();

            return handlerInput.responseBuilder
                .speak("Going home")
                .withShouldEndSession(true)
                .getResponse();

        } catch(error) {
            console.log(error);
            return handlerInput.responseBuilder
                .speak("An error occurred")
                .getResponse();
        }
    }
};

const OpenAppIntentHandler = {
    canHandle(handlerInput) {
        return Alexa.getRequestType(handlerInput.requestEnvelope) === 'IntentRequest'
            && Alexa.getIntentName(handlerInput.requestEnvelope) === 'OpenAppIntent';
    },

    async handle(handlerInput) {
        try {
            const slots = handlerInput.requestEnvelope.request.intent.slots;
            const app = slots.app && slots.app.value ? slots.app.value : "unknown";

            // Reinitialize if deleted by previous invocation
            if (!admin.apps.length) {
                admin.initializeApp({
                    credential: admin.credential.cert(serviceAccount),
                    databaseURL: "your firebase database url"
                });
            }
            const db = admin.database();

            await db.ref("tv_commands/current").set({
                action: "open",
                app: app,
                timestamp: Date.now()
            });

            await admin.app().delete();

            return handlerInput.responseBuilder
                .speak(`Opening ${app}`)
                .withShouldEndSession(true)
                .getResponse();

        } catch (error) {
            console.log(error);
            return handlerInput.responseBuilder
                .speak("An error occurred")
                .getResponse();
        }
    }
};

const SearchAppIntentHandler = {
    canHandle(handlerInput) {
        return Alexa.getRequestType(handlerInput.requestEnvelope) === 'IntentRequest'
            && Alexa.getIntentName(handlerInput.requestEnvelope) === 'SearchAppIntent';
    },
    async handle(handlerInput) {
        const currentIntent = handlerInput.requestEnvelope.request.intent;

        // If dialog is not complete, keep asking for missing slots
        if (handlerInput.requestEnvelope.request.dialogState !== 'COMPLETED') {
            return handlerInput.responseBuilder
                .addDelegateDirective(currentIntent)
                .getResponse();
        }

        // Dialog complete, all slots filled
        try {
            const slots = handlerInput.requestEnvelope.request.intent.slots;
            const app = slots.apps?.value ?? "unknown";
            const query = slots.query?.value ?? "";

            if (!admin.apps.length) {
                admin.initializeApp({
                    credential: admin.credential.cert(serviceAccount),
                    databaseURL: "you database url"
                });
            }
            const db = admin.database();

            await db.ref("tv_commands/current").set({
                action: "search",
                app: app,
                query: query,
                timestamp: Date.now()
            });

            await admin.app().delete();

            return handlerInput.responseBuilder
                .speak(`Searching ${app} for ${query}`)
                .withShouldEndSession(true)
                .getResponse();

        } catch(error) {
            console.log(error);
            return handlerInput.responseBuilder
                .speak("An error occurred")
                .getResponse();
        }
    }
};

const HelpIntentHandler = {
    canHandle(handlerInput) {
        return Alexa.getRequestType(handlerInput.requestEnvelope) === 'IntentRequest'
            && Alexa.getIntentName(handlerInput.requestEnvelope) === 'AMAZON.HelpIntent';
    },
    handle(handlerInput) {
        const speakOutput = 'You can say hello to me! How can I help?';

        return handlerInput.responseBuilder
            .speak(speakOutput)
            .reprompt(speakOutput)
            .getResponse();
    }
};

const CancelAndStopIntentHandler = {
    canHandle(handlerInput) {
        return Alexa.getRequestType(handlerInput.requestEnvelope) === 'IntentRequest'
            && (Alexa.getIntentName(handlerInput.requestEnvelope) === 'AMAZON.CancelIntent'
                || Alexa.getIntentName(handlerInput.requestEnvelope) === 'AMAZON.StopIntent');
    },
    handle(handlerInput) {
        const speakOutput = 'Goodbye!';

        return handlerInput.responseBuilder
            .speak(speakOutput)
            .getResponse();
    }
};
/* *
 * FallbackIntent triggers when a customer says something that doesn’t map to any intents in your skill
 * It must also be defined in the language model (if the locale supports it)
 * This handler can be safely added but will be ingnored in locales that do not support it yet 
 * */
const FallbackIntentHandler = {
    canHandle(handlerInput) {
        return Alexa.getRequestType(handlerInput.requestEnvelope) === 'IntentRequest'
            && Alexa.getIntentName(handlerInput.requestEnvelope) === 'AMAZON.FallbackIntent';
    },
    handle(handlerInput) {
        const speakOutput = 'Sorry, I don\'t know about that. Please try again.';

        return handlerInput.responseBuilder
            .speak(speakOutput)
            .reprompt(speakOutput)
            .getResponse();
    }
};
/* *
 * SessionEndedRequest notifies that a session was ended. This handler will be triggered when a currently open 
 * session is closed for one of the following reasons: 1) The user says "exit" or "quit". 2) The user does not 
 * respond or says something that does not match an intent defined in your voice model. 3) An error occurs 
 * */
const SessionEndedRequestHandler = {
    canHandle(handlerInput) {
        return Alexa.getRequestType(handlerInput.requestEnvelope) === 'SessionEndedRequest';
    },
    handle(handlerInput) {
        console.log(`~~~~ Session ended: ${JSON.stringify(handlerInput.requestEnvelope)}`);
        // Any cleanup logic goes here.
        return handlerInput.responseBuilder.getResponse(); // notice we send an empty response
    }
};
/* *
 * The intent reflector is used for interaction model testing and debugging.
 * It will simply repeat the intent the user said. You can create custom handlers for your intents 
 * by defining them above, then also adding them to the request handler chain below 
 * */
const IntentReflectorHandler = {
    canHandle(handlerInput) {
        return Alexa.getRequestType(handlerInput.requestEnvelope) === 'IntentRequest';
    },
    handle(handlerInput) {
        const intentName = Alexa.getIntentName(handlerInput.requestEnvelope);
        const speakOutput = `You just triggered ${intentName}`;

        return handlerInput.responseBuilder
            .speak(speakOutput)
            //.reprompt('add a reprompt if you want to keep the session open for the user to respond')
            .getResponse();
    }
};
/**
 * Generic error handling to capture any syntax or routing errors. If you receive an error
 * stating the request handler chain is not found, you have not implemented a handler for
 * the intent being invoked or included it in the skill builder below 
 * */
const ErrorHandler = {
    canHandle() {
        return true;
    },
    handle(handlerInput, error) {
        const speakOutput = 'Sorry, I had trouble doing what you asked. Please try again.';
        console.log(`~~~~ Error handled: ${JSON.stringify(error)}`);

        return handlerInput.responseBuilder
            .speak(speakOutput)
            .reprompt(speakOutput)
            .getResponse();
    }
};

/**
 * This handler acts as the entry point for your skill, routing all request and response
 * payloads to the handlers above. Make sure any new handlers or interceptors you've
 * defined are included below. The order matters - they're processed top to bottom 
 * */
exports.handler = Alexa.SkillBuilders.custom()
    .addRequestHandlers(
        LaunchRequestHandler,
        HelloWorldIntentHandler,
        OpenAppIntentHandler, → add the new intents you make
        GoHomeIntentHandler,  → add the new intents you make
        SearchAppIntentHandler, → add the new intents you make
        HelpIntentHandler,
        CancelAndStopIntentHandler,
        FallbackIntentHandler,
        SessionEndedRequestHandler,
        IntentReflectorHandler)
    .addErrorHandlers(
        ErrorHandler)
    .withCustomUserAgent('sample/hello-world/v1.2')
    .lambda();

```

---

# 8. Build and Deploy

After editing the skill:

```text
Save Model
Build Model
Deploy
```

---

# Important Alexa Locale Fix

If the Alexa simulator works but real Alexa devices do not work:

Add the same language locale as your Alexa device.

Example:

```text
English (IN)
```

inside Alexa Skill language settings.

This is a very common issue.

---

# Part 3 — Android TV App Setup

## 1. Create Android Studio Project

Use:
- Empty Activity
- Kotlin

---

## 2. Add Firebase to Android App

Add Android app in Firebase Console.

Download:

```text
google-services.json
```

Place it inside:

```text
app/
```

---

## 3. Add Firebase Dependencies

Inside `app/build.gradle.kts`:

```kotlin
implementation(platform("com.google.firebase:firebase-bom:32.8.1"))
implementation("com.google.firebase:firebase-database")
```

---

## 4. Copy the Android App Code

Get the code from:

```text
app/src/main/java/com.joshuastaralexaskillbridge
```

---

# Part 4 — Install App on Google TV

## 1. Enable Developer Options

On Google TV:

```text
Settings → System → About
```

Click:

```text
Build Number
```

7 times.

---

## 2. Enable ADB Debugging

Go to:

```text
Settings → Developer Options
```

Enable:
- USB Debugging
- Network Debugging

---

## 3. Find TV IP Address

Go to:

```text
Settings → Network
```

Example:

```text
192.168.1.45
```

---

## 4. Connect Using ADB

```bash
adb connect 192.168.1.45
```

---

## 5. Install APK

```bash
adb install app-debug.apk
```

---

# Testing

Example command:

```text
Alexa, ask {invocation name} to open YouTube
```

---

# Notes

- Firebase acts as the bridge between Alexa and the TV app
- Alexa writes commands to Firebase
- The TV app listens for changes in Firebase
- When a new command is detected, the app performs the action

---

# That's All

If you get stuck anywhere, paste the this readme into ChatGPT and follow its steps.
