package com.networking.session;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Utilidad para manejar cookies HTTP
 */
public class CookieHandler {

  private static final DateTimeFormatter COOKIE_DATE_FORMAT = DateTimeFormatter.ofPattern(
    "EEE, dd-MMM-yyyy HH:mm:ss 'GMT'"
  );

  /**
   * Parsea cookies desde el header Cookie
   * @param cookieHeader valor del header Cookie
   * @return mapa de cookies parseadas
   */
  public static Map<String, String> parseCookies(String cookieHeader) {
    Map<String, String> cookies = new HashMap<>();

    if (cookieHeader == null || cookieHeader.trim().isEmpty()) {
      return cookies;
    }

    String[] cookiePairs = cookieHeader.split(";");
    for (String cookiePair : cookiePairs) {
      String[] parts = cookiePair.trim().split("=", 2);
      if (parts.length == 2) {
        cookies.put(parts[0].trim(), parts[1].trim());
      }
    }

    return cookies;
  }

  /**
   * Crea un header Set-Cookie para una cookie de sesión
   * @param name nombre de la cookie
   * @param value valor de la cookie
   * @return header Set-Cookie formateado
   */
  public static String createSessionCookie(String name, String value) {
    return String.format(
      "%s=%s; HttpOnly; Path=/; SameSite=Strict",
      name,
      value
    );
  }

  /**
   * Crea un header Set-Cookie con opciones personalizadas
   * @param name nombre de la cookie
   * @param value valor de la cookie
   * @param maxAge tiempo de vida en segundos (null para cookie de sesión)
   * @param domain dominio de la cookie (null para usar el dominio actual)
   * @param path path de la cookie
   * @param secure si la cookie debe ser enviada solo por HTTPS
   * @param httpOnly si la cookie debe ser accesible solo por HTTP
   * @return header Set-Cookie formateado
   */
  public static String createCookie(
    String name,
    String value,
    Integer maxAge,
    String domain,
    String path,
    boolean secure,
    boolean httpOnly
  ) {
    StringBuilder cookie = new StringBuilder();
    cookie.append(name).append("=").append(value);

    if (maxAge != null) {
      cookie.append("; Max-Age=").append(maxAge);
      LocalDateTime expires = LocalDateTime.now().plusSeconds(maxAge);
      cookie
        .append("; Expires=")
        .append(expires.atOffset(ZoneOffset.UTC).format(COOKIE_DATE_FORMAT));
    }

    if (domain != null) {
      cookie.append("; Domain=").append(domain);
    }

    if (path != null) {
      cookie.append("; Path=").append(path);
    } else {
      cookie.append("; Path=/");
    }

    if (secure) {
      cookie.append("; Secure");
    }

    if (httpOnly) {
      cookie.append("; HttpOnly");
    }

    cookie.append("; SameSite=Strict");

    return cookie.toString();
  }

  /**
   * Crea un header Set-Cookie para eliminar una cookie
   * @param name nombre de la cookie a eliminar
   * @param path path de la cookie
   * @return header Set-Cookie para eliminar la cookie
   */
  public static String deleteCookie(String name, String path) {
    return createCookie(name, "", 0, null, path, false, true);
  }

  /**
   * Valida si un nombre de cookie es válido según RFC 6265
   * @param name nombre de la cookie
   * @return true si el nombre es válido
   */
  public static boolean isValidCookieName(String name) {
    if (name == null || name.trim().isEmpty()) {
      return false;
    }

    // Caracteres no permitidos en nombres de cookies según RFC 6265
    String invalidChars = "\"(),/:;<=>?@[\\]{}";
    for (char c : invalidChars.toCharArray()) {
      if (name.indexOf(c) >= 0) {
        return false;
      }
    }

    return true;
  }

  /**
   * Escapa caracteres especiales en el valor de una cookie
   * @param value valor a escapar
   * @return valor escapado
   */
  public static String escapeCookieValue(String value) {
    if (value == null) {
      return "";
    }

    // Escapar caracteres especiales básicos
    return value.replace("\"", "\\\"").replace(";", "\\;").replace(",", "\\,");
  }
}
