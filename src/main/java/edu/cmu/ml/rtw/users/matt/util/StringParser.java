package edu.cmu.ml.rtw.users.matt.util;

/**
 * Trivial, but necessary if you want to use Strings with Index.
 */
public class StringParser implements ObjectParser<String> {
  public String fromString(String string) {
    // We call new here, in case this was from a substring, or something, which would keep a
    // reference to the original (longer) string.
    return new String(string);
  }
}
