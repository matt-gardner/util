package com.mattg.util;

/**
 * Trivial, but necessary if you want to use Strings with Index.
 */
public class StringParser implements ObjectParser<String> {
  public String fromString(String string) {
    return string;
  }
}
