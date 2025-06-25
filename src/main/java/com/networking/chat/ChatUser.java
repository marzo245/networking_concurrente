package com.networking.chat;

import java.time.LocalDateTime;

/**
 * Representa un usuario del chat
 */
public class ChatUser {

  private final String id;
  private final String username;
  private final LocalDateTime joinedAt;
  private volatile LocalDateTime lastActivity;
  private volatile boolean online;

  public ChatUser(String id, String username) {
    this.id = id;
    this.username = username;
    this.joinedAt = LocalDateTime.now();
    this.lastActivity = LocalDateTime.now();
    this.online = true;
  }

  public String getId() {
    return id;
  }

  public String getUsername() {
    return username;
  }

  public LocalDateTime getJoinedAt() {
    return joinedAt;
  }

  public LocalDateTime getLastActivity() {
    return lastActivity;
  }

  public void updateActivity() {
    this.lastActivity = LocalDateTime.now();
  }

  public boolean isOnline() {
    return online;
  }

  public void setOnline(boolean online) {
    this.online = online;
    if (online) {
      updateActivity();
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;
    ChatUser chatUser = (ChatUser) obj;
    return id.equals(chatUser.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return String.format(
      "ChatUser{id='%s', username='%s', online=%s}",
      id,
      username,
      online
    );
  }
}
