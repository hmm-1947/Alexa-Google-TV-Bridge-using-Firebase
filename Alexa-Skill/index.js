const Alexa = require('ask-sdk-core');
const admin = require('firebase-admin');


const serviceAccount = require('./firebase-key.json');
const DB_URL = "https://try123-73326-default-rtdb.firebaseio.com/";

const getDb = () => {
    if (!admin.apps.length) {
        admin.initializeApp({
            credential: admin.credential.cert(serviceAccount),
            databaseURL: DB_URL
        });
    }
    return admin.database();
};

const sendCommand = async (payload) => {
    const db = getDb();
    await db.ref("tv_commands/current").set({
        ...payload,
        timestamp: Date.now()
    });
    await admin.app().delete();
};

const speak = (handlerInput, text) =>
    handlerInput.responseBuilder
        .speak(text)
        .withShouldEndSession(true)
        .getResponse();

const isIntent = (handlerInput, ...names) =>
    Alexa.getRequestType(handlerInput.requestEnvelope) === 'IntentRequest' &&
    names.includes(Alexa.getIntentName(handlerInput.requestEnvelope));

const withErrorHandling = (handlerInput, fn) =>
    fn().catch(err => {
        console.error(err);
        return handlerInput.responseBuilder.speak("An error occurred").getResponse();
    });


const OpenAppIntentHandler = {
    canHandle: (h) => isIntent(h, 'OpenAppIntent'),
    handle: (h) => withErrorHandling(h, async () => {
        const app = h.requestEnvelope.request.intent.slots.app?.value ?? "unknown";
        await sendCommand({ action: "open", app });
        return speak(h, `Opening ${app}`);
    })
};

const GoHomeIntentHandler = {
    canHandle: (h) => isIntent(h, 'GoHomeIntent'),
    handle: (h) => withErrorHandling(h, async () => {
        await sendCommand({ action: "home" });
        return speak(h, "Going home");
    })
};

const PowerOffIntentHandler = {
    canHandle: (h) => isIntent(h, 'PowerOffIntent'),
    handle: (h) => withErrorHandling(h, async () => {
        await sendCommand({ action: "power_off" });
        return speak(h, "Turning off TV");
    })
};

const PowerOnIntentHandler = {
    canHandle: (h) => isIntent(h, 'PowerOnIntent'),
    handle: (h) => withErrorHandling(h, async () => {
        await sendCommand({ action: "power_on" });
        return speak(h, "Turning on TV");
    })
};

const SearchAppIntentHandler = {
    canHandle: (h) => isIntent(h, 'SearchAppIntent'),
    handle: (h) => withErrorHandling(h, async () => {
        const intent = h.requestEnvelope.request.intent;
        if (h.requestEnvelope.request.dialogState !== 'COMPLETED') {
            return h.responseBuilder.addDelegateDirective(intent).getResponse();
        }
        const app   = intent.slots.apps?.value  ?? "unknown";
        const query = intent.slots.query?.value ?? "";
        await sendCommand({ action: "search", app, query });
        return speak(h, `Searching ${app} for ${query}`);
    })
};

const CancelAndStopIntentHandler = {
    canHandle: (h) => isIntent(h, 'AMAZON.CancelIntent', 'AMAZON.StopIntent'),
    handle: (h) => h.responseBuilder.speak("Goodbye!").getResponse()
};

const SessionEndedRequestHandler = {
    canHandle: (h) => Alexa.getRequestType(h.requestEnvelope) === 'SessionEndedRequest',
    handle: (h) => { console.log(`Session ended: ${JSON.stringify(h.requestEnvelope)}`); return h.responseBuilder.getResponse(); }
};

const ErrorHandler = {
    canHandle: () => true,
    handle: (h, error) => {
        console.error(`Error: ${JSON.stringify(error)}`);
        return h.responseBuilder.speak("Sorry, I had trouble doing what you asked.").reprompt("Try again.").getResponse();
    }
};


exports.handler = Alexa.SkillBuilders.custom()
    .addRequestHandlers(
        OpenAppIntentHandler,
        GoHomeIntentHandler,
        PowerOffIntentHandler,
        PowerOnIntentHandler,
        SearchAppIntentHandler,
        CancelAndStopIntentHandler,
        SessionEndedRequestHandler
    )
    .addErrorHandlers(ErrorHandler)
    .withCustomUserAgent('tv-bridge/v2')
    .lambda();