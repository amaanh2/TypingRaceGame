package com.example.webchatserver;

import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import org.json.JSONObject;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@ServerEndpoint(value="/ws/{roomID}")
public class ChatServer {

    // Maps for storing user data and state by room
    private static final Map<String, Map<String, String>> roomUsernames = new HashMap<>(); // track usernames
    private static final Map<String, Integer> userWins = new HashMap<>(); // track user wins
    private static final Map<String, Set<String>> roomReadyUsers = new HashMap<>(); // track readied up users
    private static Map<String, String> roomLastChallengeMessage = new HashMap<>(); // track last challenge

    private static Map<String, Boolean> roomFirstCorrectResponse = new HashMap<>(); // check so second or more place doesnt get a win
    private static final Random random = new Random();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1); // used for countdown function


    /**

     * This class represents a web socket server, a new connection is created and it receives a roomID as a parameter

     * **/
    @OnOpen
    public void open(@PathParam("roomID") String roomID, Session session) throws IOException {
        String time = getTime();
        // variables, on start if theres not stuff, put stuff in, but dont put stuff to null if another user comes
        roomUsernames.putIfAbsent(roomID, new HashMap<>());
        roomReadyUsers.putIfAbsent(roomID, new HashSet<>());
        roomLastChallengeMessage.putIfAbsent(roomID, "");
        roomFirstCorrectResponse.putIfAbsent(roomID, false);
        // created formatMessage function to look cleaner.
        session.getBasicRemote().sendText(formatMessage(time, "Server", "Welcome to the type race: " + roomID + ". Please state your username to begin."));
    }

    @OnClose
    public void close(@PathParam("roomID") String roomID, Session session) throws IOException, EncodeException {
        String userId = session.getId();
        String time = getTime();
        // Cleanup user data on connection close
        Map<String, String> usernames = roomUsernames.get(roomID);
        Set<String> readyUsers = roomReadyUsers.get(roomID);

        if (usernames != null && usernames.containsKey(userId)) {
            String username = usernames.remove(userId);
            readyUsers.remove(userId);
            userWins.remove(userId); // Remove their wins record
            // Broadcast that user has left the chat room
            broadcast(session, roomID, formatMessage(time, "Server", username + " left the chat room."));
        }
    }

    @OnMessage
    public void handleMessage(@PathParam("roomID") String roomID, String comm, Session session) throws IOException, EncodeException {
//        example getting unique userID that sent this message
        String userId = session.getId();
        String time = getTime();
        JSONObject jsonmsg = new JSONObject(comm);
//        Example conversion of json messages from the client
        String message = (String) jsonmsg.get("msg");

        Map<String, String> usernames = roomUsernames.get(roomID);
        Set<String> readyUsers = roomReadyUsers.get(roomID); // use a set so errors cant occur by readying up more than once etc

        // handle the messages

        if (!usernames.containsKey(userId)) {
            // Setup new user and initialize their win count
            usernames.put(userId, message);
            userWins.put(userId, 0); // default wins to 0 on creation
            broadcast(session, roomID, formatMessage(time, "Server", message + " joined the chat room."));
            session.getBasicRemote().sendText(formatMessage(time, "Server", " Type 'Ready' to ready up!"));
        } else {
            String username = usernames.get(userId);
            int wins = userWins.getOrDefault(userId, 0);
            if ("Ready".equalsIgnoreCase(message.trim()))  { // if message is ready or "Ready" or "Ready     " it will work to ready the player
                readyUsers.add(userId);
                broadcast(session, roomID, formatMessage(time, "Server", username + " is ready!"));
                // Start a new game only if all users are ready
                if (readyUsers.size() == session.getOpenSessions().size()) {
                    beginCountdown(session, roomID, 5);
                    roomFirstCorrectResponse.put(roomID, false);  // Reset for the new challenge
                    readyUsers.clear();  // Clear ready users after starting the countdown
                }
              // if message equals last challenge message and first correct is not true
            } else if (message.equals(roomLastChallengeMessage.get(roomID))  && !roomFirstCorrectResponse.get(roomID)) {
                // add win and set first correct to true so this code cannot run again, is set false once everyone is ready again
                userWins.put(userId, wins += 1);
                roomFirstCorrectResponse.put(roomID, true);
                broadcast(session, roomID, formatMessage(time, "Server", username + " correctly typed the message first and now has " + wins + " wins!"));
            } else {
                // default message if they just wanna talk
                broadcast(session, roomID, formatMessage(time, "(" + username + ")" + "[" + wins + " Win(s)]", message));

            }
        }
    }

    private void beginCountdown(Session session, String roomID, int count) throws IOException {


        // countdown from count variable when given
        scheduler.schedule(new Runnable() { // make code run after 1 second. this is NEEDED because its a RECURSIVE function that calls itself as a countdown!!! it will call say 5 in chat, wait 1 second, 4 and wait 1 second etc!!!


            public void run() { // needed to be named run! --->do not change <---
                try {
                    if (count > 0) { //recursive callback while its greater than 0
                        broadcast(session, roomID, formatMessage(getTime(), "Server", "Counting down from: " + count));
                        beginCountdown(session, roomID, count -1);
                    } else {
                        //once its not greater than 0 get random message from string array and broadcast the message
                        String challengeMessage = getRandomMessage(); // simplified into method to keep run method clean as recursive is confusing
                        roomLastChallengeMessage.put(roomID, challengeMessage); // store to check for message later
                        broadcast(session, roomID, formatMessage(getTime(), "Server", "Type this fast: " + challengeMessage));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, 1, TimeUnit.SECONDS);
    }

    private void broadcast(Session session, String roomID, String message) throws IOException {
        //method to broadcast messages to all users in the room, gets SUPER repetitive
        for (Session peer : session.getOpenSessions()) {
            if (peer.isOpen() && roomID.equals(peer.getPathParameters().get("roomID"))) {
                peer.getBasicRemote().sendText(message);
            }
        }
    }


// send messages to look cleaner, dont need all the \type\ \chat\ etc
    private String formatMessage(String time, String username, String message) {
        return "{\"type\": \"chat\", \"message\":\"[" + time + "] " + username + ": " + message + "\"}";
    }

    private String getRandomMessage() {
        String[] messages = {"Hello World!", "Java is fun!", "WebSockets are cool!", "Time to code!", "Luke is cool", "Zhaojun is awesome", "Ryan is amazing", "Amaan is great"};
        return messages[random.nextInt(messages.length)]; // return a random number gotten from 0 - messages.length
    }

    public static String getTime() {
        // get time
        LocalTime currentTime = LocalTime.now();

        // format to hour:minute:second
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");

        // turn into string
        String formattedTime = currentTime.format(formatter);
        return formattedTime;
    }


}
