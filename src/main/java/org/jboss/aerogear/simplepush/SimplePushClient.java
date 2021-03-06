/**
 * JBoss, Home of Professional Open Source
 * Copyright Red Hat, Inc., and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.aerogear.simplepush;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_10;
import org.java_websocket.handshake.ServerHandshake;
import org.jboss.aerogear.simplepush.protocol.Ack;
import org.jboss.aerogear.simplepush.protocol.MessageType;
import org.jboss.aerogear.simplepush.protocol.impl.*;
import org.jboss.aerogear.simplepush.protocol.impl.json.JsonUtil;
import org.jboss.aerogear.simplepush.util.UUIDUtil;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple Push Client
 */
public class SimplePushClient {

    private final WebSocketClient websocketClient;
    private RegistrationListener registrationListener;
    private MessageListener listener;
    private List<String> registeredChannels = new ArrayList<>();

    public SimplePushClient(String simplePushServerURL) {
        final URI serverUri;
        try {
            serverUri = new URI(simplePushServerURL);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("invalid simplePushEndpoint url", e);
        }
        websocketClient = new WebSocketClient(serverUri, new Draft_10()) {

            @Override
            public void onOpen(ServerHandshake handshake) {
            }

            @Override
            public void onMessage(String message) {
                final MessageType messageType = JsonUtil.parseFrame(message);
                switch (messageType.getMessageType()) {
                case REGISTER:
                    final RegisterResponseImpl registerResponse = JsonUtil.fromJson(message, RegisterResponseImpl.class);
                    if (registrationListener != null) {
                        registrationListener.onRegistered(registerResponse.getChannelId(), registerResponse.getPushEndpoint());
                    }
                    break;
                case NOTIFICATION:
                    final NotificationMessageImpl notificationMessage = JsonUtil.fromJson(message, NotificationMessageImpl.class);
                    for (Ack ack : notificationMessage.getAcks()) {
                        send(JsonUtil.toJson(ack));
                        listener.onMessage(ack);
                    }
                    break;
                case UNREGISTER:
                    UnregisterMessageImpl unregisterMessage = JsonUtil.fromJson(message, UnregisterMessageImpl.class);
                    registeredChannels.remove(unregisterMessage.getChannelId());
                    break;
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
            }

            @Override
            public void onError(Exception ex) {
                throw new RuntimeException("error in communication channel with simple push server", ex);
            }
        };
    }

    /**
     * Create a connection with the Simple Push Server.
     */
    public void connect() {
        websocketClient.connect();
        final HelloMessageImpl helloMessage = new HelloMessageImpl(UUIDUtil.newUAID());
        websocketClient.send(JsonUtil.toJson(helloMessage));
    }

    /**
     * Get the channel id by index.
     * 
     * @param index the index of the registered channel
     * @return the index or null
     */
    public String getChannelId(int index) {
        return registeredChannels.get(index);
    }

    /**
     * Register to a new channel for notification by the simple push server
     * 
     * @param registrationListener called when the registration is complete
     */
    public void register(RegistrationListener registrationListener) {
        this.registrationListener = registrationListener;
        String channelId = UUIDUtil.newUAID();
        registeredChannels.add(channelId);
        final String register = JsonUtil.toJson(new RegisterMessageImpl(channelId));
        websocketClient.send(register);
    }

    /**
     * Remove the registered channel
     * 
     * @param channelId the previously registered channel id
     */
    public void unregister(String channelId) {
        UnregisterMessageImpl unregisterMessage = new UnregisterMessageImpl(channelId);
        websocketClient.send(JsonUtil.toJson(unregisterMessage));
    }

    /**
     * Close the communication
     */
    public void close() {
        try {
            websocketClient.closeBlocking();
        } catch (InterruptedException e) {
            // ignore
        }
    }

    public void addMessageListener(MessageListener listener) {
        this.listener = listener;
    }
}
