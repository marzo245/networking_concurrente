package com.networking.chat;

import java.time.LocalDateTime;

/**
 * Representa un mensaje en el chat
 */
public class ChatMessage {

  private final String id;
  private final ChatUser user;
  private final String content;
  private final LocalDateTime timestamp;
  private final MessageType type;

  public enum MessageType {
    TEXT,
    NOTIFICATION,
    SYSTEM,
  }

  public ChatMessage(ChatUser user, String content) {
    this(user, content, MessageType.TEXT);
  }

  public ChatMessage(ChatUser user, String content, MessageType type) {
    this.id = generateId();
    this.user = user;
    this.content = content;
    this.timestamp = LocalDateTime.now();
    this.type = type;
  }

  public String getId() {
    return id;
  }

  public ChatUser getUser() {
    return user;
  }

  public String getContent() {
    return content;
  }

  public LocalDateTime getTimestamp() {
    return timestamp;
  }

  public MessageType getType() {
    return type;
  }

  private String generateId() {
    return System.currentTimeMillis() + "-" + System.nanoTime();
  }

  @Override
  public String toString() {
    return String.format(
      "ChatMessage{id='%s', user=%s, content='%s', timestamp=%s, type=%s}",
      id,
      user.getUsername(),
      content,
      timestamp,
      type
    );
  }
}
