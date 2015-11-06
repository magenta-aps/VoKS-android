/**
 * BComeSafe, http://bcomesafe.com
 * Copyright 2015 Magenta ApS, http://magenta.dk
 * Licensed under MPL 2.0, https://www.mozilla.org/MPL/2.0/
 * Developed in co-op with Baltic Amadeus, http://baltic-amadeus.lt
 */

package com.bcomesafe.app;

/**
 * Application constants
 * All members of this class are immutable.
 */
public final class Constants {

    // Other
    public static final int INVALID_INT_ID = -1;
    public static final long INVALID_LONG_ID = -1L;
    public static final String INVALID_STRING_ID = "";

    // Languages
    public static final String LANGUAGE_CODE_EN = "en";
    public static final String LANGUAGE_CODE_NO = "no";

    // Extras
    public static final String EXTRA_SKIP_INITIAL_MUTE = "skip_initial_mute";

    // Chat message types
    public static final int MESSAGE_TYPE_OWN = 1;
    public static final int MESSAGE_TYPE_OPERATOR = 2;
    public static final int MESSAGE_TYPE_PUSH = 3;
    public static final int MESSAGE_TYPE_APP = 4;

    // Shared preferences names
    public static final String SHARED_PREFERENCES_APP_USER = "as_app_user";
    public static final String SHARED_PREFERENCES_REQUESTS = "as_app_requests";

    // Requests
    public static final String REQUEST_ERROR_ACTION = "lt.balticamadeus.alarmsystem.requests.RequestManager.requestErrorAction";
    public static final String REQUEST_COMPLETED_ACTION = "lt.balticamadeus.alarmsystem.requests.RequestManager.requestCompletedAction";
    public static final String REQUEST_ID_EXTRA = "lt.balticamadeus.alarmsystem.requests.RequestManager.requestIdExtra";
    public static final String REQUEST_STATUS_CODE_EXTRA = "lt.balticamadeus.alarmsystem.requests.RequestManager.statusCodeExtra";
    public static final String REQUEST_MESSAGE_EXTRA = "lt.balticamadeus.alarmsystem.requests.RequestManager.messageExtra";
    public static final String REQUEST_RESPONSE_EXTRA = "lt.balticamadeus.alarmsystem.requests.RequestManager.responseExtra";
    public static final String REQUEST_STACK_TRACE_EXTRA = "lt.balticamadeus.alarmsystem.requests.RequestManager.stackTraceExtra";

    // WebServices responses
    // HTTP status codes
    public static final int HTTP_STATUS_CODE_OK = 200;
    public static final int HTTP_STATUS_CODE_NOT_FOUND = 404;
    public static final int HTTP_STATUS_CODE_NOT_AVAILABLE = 401;
    public static final int HTTP_STATUS_CODE_REDIRECT_1 = 301;
    public static final int HTTP_STATUS_CODE_REDIRECT_2 = 302;
    public static final int HTTP_STATUS_CODE_INTERNAL_SERVER_ERROR = 500;
    public static final int HTTP_STATUS_BAD_REQUEST = 400;
    public static final int HTTP_STATUS_INTERNAL_APP_ERROR = -200;

    // Requests
    // Requests parameters
    // General
    public static final String REQUEST_PARAM_ANDROID = "android";
    // Other
    public static final String REQUEST_PARAM_DEVICE_TYPE = "device_type";
    public static final String REQUEST_PARAM_DEVICE_ID = "device_id";
    public static final String REQUEST_PARAM_GCM_ID = "gcm_id";
    public static final String REQUEST_PARAM_DEVICE_MAC = "mac_address";
    public static final String REQUEST_PARAM_LANGUAGE = "lang";
    public static final String REQUEST_PARAM_CALL_POLICE = "call_police";
    public static final String REQUEST_PARAM_NOTIFICATION_ID = "notification_id";
    public static final String REQUEST_PARAM_REMOTE_LOG = "message";
    // Response parameters
    public static final String REQUEST_RESPONSE_PARAM_SUCCESS = "success";
    public static final String REQUEST_RESPONSE_PARAM_SHELTER_ID = "shelter_id";
    public static final String REQUEST_RESPONSE_PARAM_WS_URL = "ws_url";
    public static final String REQUEST_RESPONSE_PARAM_API_URL = "api_url";
    public static final String REQUEST_RESPONSE_PARAM_DEV_MODE = "dev_mode";
    public static final String REQUEST_RESPONSE_PARAM_MESSAGE = "message";

    // Broadcast actions
    public static final String ACTION_GOT_IT = "lt.balticamadeus.alarmsystem.action.GotIt";
    public static final String ACTION_SHOW_GCM_MESSAGE_IN_CHAT = "lt.balticamadeus.alarmsystem.action.ShowGCMMessageInChat";

    // WebSocket connection parameters
    // Default
    public static final String WEBSOCKET_PARAM_TYPE = "type";
    public static final String WEBSOCKET_PARAM_PAYLOAD = "payload";
    public static final String WEBSOCKET_PARAM_DST = "dst";
    public static final String WEBSOCKET_PARAM_SRC = "src";
    public static final String WEBSOCKET_PARAM_SDP_MID = "sdpMid";
    public static final String WEBSOCKET_PARAM_SDP_M_LINE_INDEX = "sdpMLineIndex";
    public static final String WEBSOCKET_PARAM_CANDIDATE = "candidate";
    public static final String WEBSOCKET_PARAM_SDP = "sdp";
    // From shelter
    public static final String WEBSOCKET_PARAM_DATA = "data";
    public static final String WEBSOCKET_PARAM_TIMESTAMP = "timestamp";

    // WebSocket connection data types
    // Default
    public static final String WEBSOCKET_TYPE_CANDIDATE = "CANDIDATE";
    public static final String WEBSOCKET_TYPE_ANSWER = "ANSWER";
    public static final String WEBSOCKET_TYPE_OFFER = "OFFER";
    public static final String WEBSOCKET_TYPE_BYE = "BYE";
    // From shelter
    public static final String WEBSOCKET_TYPE_PEER_CONNECTION = "PEER_CONNECTION";
    public static final String WEBSOCKET_TYPE_SHELTER_STATUS = "SHELTER_STATUS";
    public static final String WEBSOCKET_TYPE_SHELTER_RESET = "SHELTER_RESET";
    public static final String WEBSOCKET_TYPE_CHAT_MESSAGE = "MESSAGE";
    public static final String WEBSOCKET_TYPE_CHAT_MESSAGES = "MESSAGES";
    public static final String WEBSOCKET_TYPE_REQUEST_CALL = "REQUEST_CALL";
    public static final String WEBSOCKET_TYPE_LISTENING = "LISTENING";
    public static final String WEBSOCKET_TYPE_VIDEO = "VIDEO";
    public static final String WEBSOCKET_TYPE_BATTERY_LEVEL = "BATTERY_LEVEL";
    public static final String WEBSOCKET_TYPE_PING = "PING";
    public static final String WEBSOCKET_TYPE_PONG = "PONG";

    // WebSocket connection data
    // Default
    public static final String WEBSOCKET_DATA_OFFER_UP = "OFFER";
    public static final String WEBSOCKET_DATA_OFFER_LO = "offer";
    public static final String WEBSOCKET_DATA_ANSWER_LO = "answer";
    public static final String WEBSOCKET_DATA_CANDIDATE_UP = "CANDIDATE";

    // Shelter statuses
    public static final int SHELTER_STATUS_NOT_KNOWN = -1;
    public static final int SHELTER_STATUS_OFF = 0;
    public static final int SHELTER_STATUS_ON = 1;
    // Shelter listening
    public static final int SHELTER_NOT_LISTENING = 0;
    public static final int SHELTER_LISTENING = 1;
    // Shelter watching (video)
    public static final int SHELTER_NOT_WATCHING = 0;
    public static final int SHELTER_WATCHING = 1;
    // Currently unused
    @SuppressWarnings("unused")
    public static final int SHELTER_CALL_STATE_ON_HOLD = 2;

    // Crisis center call state
    public static final int CC_CALL_NOT_AVAILABLE = -1;
    public static final int CC_CALL_AVAILABLE = 0;
    public static final int CC_CALL_WAITING_ANSWER = 1;
    public static final int CC_CALL_ONGOING = 2;
    public static final int CC_CALL_ONHOLD = 3;

    // GCM message structure
    public static final String GCM_MESSAGE_OBJECT = "GCM_MESSAGE";
    public static final String GCM_MESSAGE_ID = "id";
    public static final String GCM_MESSAGE_TITLE = "title";
    public static final String GCM_MESSAGE_CONTENT = "content";
    public static final String GCM_MESSAGE_TIMESTAMP = "timestamp";
}
