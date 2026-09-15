package com.nexusworld.application.graph;
public class GraphStoreUnavailableException extends RuntimeException {
  public GraphStoreUnavailableException(String message) { super(message); }
  public GraphStoreUnavailableException(String message, Throwable cause) { super(message, cause); }
}
